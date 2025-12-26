package org.example.backend.utils;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

@Component  // 添加这个注解
public class AiTypeConversion {

    public static String extractTextFromMessage(ChatMessage message) {
        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            return userMessage.singleText();
        } else if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            try {
                return aiMessage.text();
            } catch (Exception e) {
                return aiMessage.toString().replaceAll(".*text='", "").replaceAll("'.*", "");
            }
        } else if (message instanceof SystemMessage) {
            SystemMessage systemMessage = (SystemMessage) message;
            try {
                return systemMessage.text();
            } catch (Exception e) {
                return systemMessage.toString().replaceAll(".*text='", "").replaceAll("'.*", "");
            }
        }
        return "未知消息类型";
    }
}
