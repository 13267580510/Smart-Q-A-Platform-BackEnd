package org.example.backend.dto;

import org.example.backend.model.User;
import org.example.backend.model.UserAvatar;
import org.example.backend.model.UserRole;
import org.example.backend.repository.AnswerRepository;
import org.example.backend.repository.QuestionRepository;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

public record UserDTO(
        Long id,
        String username,
        String email,
        String role,
        String nickname,
        List<String> authorities,
        String status,
        String introduction,
        String age,
        String residence,
        String avatarPath,
        Integer questionCount,
        Integer answerCount,
        User.Gender sex // 添加性别字段
) implements Serializable {
    // 静态工厂方法：从 User 实体创建 DTO
    public static UserDTO fromUser(User user,QuestionRepository questionRepository, AnswerRepository answerRepository) {
        String avatarPath = null;
        if (user.getUserAvatar() != null) {
            avatarPath = user.getUserAvatar().getAvatarPath();
        }
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getNickname(),
                mapAuthorities(user),
                user.getStatus(),
                user.getIntroduction(),
                user.getAge() != null ? user.getAge().toString() : null,
                user.getResidence(),
                avatarPath,
                getQuestionCount(user, questionRepository),
                getAnswerCount(user, answerRepository),
                user.getSex()
        );
    }

    // 私有辅助方法：映射权限列表
    private static List<String> mapAuthorities(User user) {
        return user.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toList());
    }

    private static Integer getQuestionCount(User user, QuestionRepository questionRepository) {
        return questionRepository.countByAuthor_Id(user.getId());
    }

    private static Integer getAnswerCount(User user, AnswerRepository answerRepository) {
        return answerRepository.countByAuthor_Id(user.getId());
    }
}
