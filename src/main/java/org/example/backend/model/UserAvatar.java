package org.example.backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "user_avatars")
public class UserAvatar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "avatar_path", nullable = false)
    private String avatarPath;

    public UserAvatar() {}
    public UserAvatar(User user, String avatarPath) {
        this.user = user;
        this.avatarPath = avatarPath;
    }
}