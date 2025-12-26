package org.example.backend.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

@Configuration
public class RagConfig {

    // 关键修改：移除 @Autowired，改为方法参数注入
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    // 关键修改：将依赖作为方法参数注入
    @Bean
    public ContentRetriever contentRetriever(
            EmbeddingModel aliEmbeddingModel,  // Spring 会自动注入
            EmbeddingStore<TextSegment> embeddingStore  // Spring 会自动注入
    ) throws IOException {

        // 📂 第一步：加载知识库文档
        String docsPath = new ClassPathResource("docs").getFile().getAbsolutePath();
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(docsPath);

        // ✂️ 第二步：准备文档切割器
        DocumentByParagraphSplitter paragraphSplitter = new DocumentByParagraphSplitter(
                1000,  // 每个文本块最多1000个字符
                200    // 块之间重叠200字符
        );

        // 🚀 第三步：创建文档处理流水线
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(paragraphSplitter)
                .textSegmentTransformer(textSegment -> TextSegment.from(
                        " " + textSegment.metadata().getString("source") + "\n" + textSegment.text(),
                        textSegment.metadata()
                ))
                .embeddingModel(aliEmbeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // 📦 第四步：处理并存储所有文档
        ingestor.ingest(documents);

        // 🔍 第五步：创建智能检索器
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(aliEmbeddingModel)
                .maxResults(3)
                .minScore(0.65)
                .build();

        return contentRetriever;
    }
}