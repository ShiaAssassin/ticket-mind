package com.ticketmind.repository;

import java.util.Optional;

import com.ticketmind.model.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户账号的数据访问接口。
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /** 登录认证时按用户名加载账号。 */
    Optional<UserAccount> findByUsername(String username);
}
