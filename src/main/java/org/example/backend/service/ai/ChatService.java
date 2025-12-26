package org.example.backend.service.ai;
import dev.langchain4j.service.*;
import org.example.backend.service.ai.domain.Report;
import reactor.core.publisher.Flux;

public interface ChatService {

    @SystemMessage(fromResource = "tip.txt")
    String chat (String message);
    @SystemMessage(fromResource = "tip.txt")
    Report getReport(String message);
    /**
     * 使用RAG技术与AI进行对话
     * @param message 用户输入的消息内容
     * @return 包含RAG处理结果的封装对象
     *
    - AI生成的回答内容
    - 相关的检索来源信息
     */
    @SystemMessage(fromResource = "tip.txt")
    Result<String> getChatRag(String message);

    /**
     *
     * @param memoryId  会话ID，用于标识和维持对话的上下文记忆
     * @param message   用户消息
     * @return 返回一个流失输出
     */
    @SystemMessage(fromResource = "tip.txt")
    Flux<String> sseChat(@MemoryId String  memoryId, @UserMessage String message );
}
