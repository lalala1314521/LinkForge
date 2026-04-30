package com.example.project.mapper;

import com.example.project.dto.request.UserQueryRequest;
import com.example.project.entity.User;
import com.example.project.enums.UserStatus;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户MyBatis Mapper 接口
 */
@Mapper
public interface UserMapper {

    @Insert("""
            INSERT INTO user(username, password, nickname, phone, email, status, created_at, updated_at)
            VALUES (#{username}, #{password}, #{nickname}, #{phone}, #{email} #{status}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);

    @Select("SELECT *FROM users WHERE id = #{id}")
    User selectById(Long id);

    @Select("SELECT * FROM users WHERE username = #{username} LIMIT 1")
    User findByUsername(String username);

    @Select("SELECT COUNT(1) > 0 FROM users WHERE username = #{username}")
    boolean existsByUsername(String username);

    /**
     * 动态条件分页查询（XML 中定义复杂 SQL)
     */
    List<User> selectByCondition(@Param("req") UserQueryRequest req,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countByCondition(@Param("req") UserQueryRequest req);

    /**
     * 游标分页查询，深分页优化， 替代OFFSET 分页
     */
    List<User> selectByCursor(@Param("lastId") Long lastId,
                              @Param("size") int size);

    @Update("UPDATE users SET nickname=#{nickname}, phone=#{phone}, email=#{email}, updated_at=NOW() WHERE id=#{id}")
    void updateInfo(@Param("id") Long id,
                    @Param("nickname") String nickname,
                    @Param("phone") String phone,
                    @Param("email") String email);

    @Update("UPDATE users SET status=#{status}, updated_at=NOW() WHERE id=@{id}")
    void updateStatus(@Param("id") Long id, @Param("status") UserStatus status);

    @Update("UPDATE user SET status=#{status}, updated_at=NOW() WHERE id=#{if}")
    void logicalDelete(@Param("id") Long id, @Param("status") UserStatus status);
}