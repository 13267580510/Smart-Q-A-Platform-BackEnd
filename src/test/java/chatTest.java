import dev.langchain4j.service.Result;
import org.example.backend.BackendApplication;
import org.example.backend.service.ai.ChatService;
import org.example.backend.service.ai.domain.Report;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = BackendApplication.class)
public class chatTest {
    @Autowired
    private ChatService chatService;
    @Test
    public void test() {
       String res =  chatService.chat("我想在后端开发和网络安全中选择后端开发");
       System.out.println(res);
         res =  chatService.chat("你有什么建议吗？");
        System.out.println(res);
        res =  chatService.chat("我一开始说要选哪个来着？");
        System.out.println(res);
    }

    @Test
    public void test_GetRepot() {
        Report report = chatService.getReport("我是一名后端，我想学习后端相关知识");
        System.out.println(report.toString());
    }

    @Test
    public void test_RAG() {
      String chat =  chatService.chat("智域里面有没有关于代码冲突的问题");
      System.out.println(chat);
    }

    @Test
    public void test_RAG2() {
        Result<String> res =  chatService.getChatRag("智域里面有没有关于代码冲突的问题");
        System.out.println("小智："+res.content()+"\n信息来源:"+res.sources());
    }
}
