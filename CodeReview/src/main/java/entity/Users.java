package entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "Users")
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "username",  nullable = false)
    private String username;

    @Column(name = "email",   nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash",  nullable = false,  unique = true)
    private String password;

    @Column(name = "role",   nullable = false)
    private String role;

    @CreationTimestamp
    @Column(name = "create_at",  nullable = false)
    private LocalDateTime createdAt;

}
