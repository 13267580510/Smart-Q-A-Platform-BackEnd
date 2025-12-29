package org.example.backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Data
@Table(name = "users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User implements UserDetails {
    // UserDetails接口实现
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == UserRole.ADMIN) {
            return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        } else {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"BANNED".equals(status) || (banEndTime != null && banEndTime.isBefore(LocalDateTime.now()));
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username",unique = true, nullable = false) // 显式映射数据库列名
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    private String email;

    @Enumerated(EnumType.STRING)
    private UserRole role;
    public enum UserRole {
        USER,ADMIN
    }
    @Column(name = "nickname", nullable = false)
    private String nickname;

    @OneToMany(mappedBy = "author")
    @ToString.Exclude
    @JsonIgnore
    private List<Question> questions = new ArrayList<>();

    @OneToMany(mappedBy = "author")
    @ToString.Exclude
    @JsonIgnore
    private List<Answer> answers = new ArrayList<>();

    @Column(name = "status")
    private String status;

    @Column(name = "ban_end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime banEndTime;

    // 新增介绍字段
    @Column(name = "introduction")
    private String introduction;

    // 新增年龄字段
    @Column(name = "age")
    private String age;

    // 新增居住地字段
    @Column(name = "residence")
    private String residence;

    // 关联用户头像
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    @JsonIgnore
    private UserAvatar userAvatar;



    @Column(name = "sex")
    @Enumerated(EnumType.STRING) // 以字符串形式存储枚举值
    private Gender sex;

    public enum Gender {
        MAN,WOMAN
    }


    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", status='" + status + '\'' +
                ", introduction='" + introduction + '\'' +
                ", role=" + role +
                '}';
    }

    public User(){};
}