package com.example.project.mapper;


import com.example.project.entity.UserPoints;
import org.apache.ibatis.annotations.*;
import org.checkerframework.checker.guieffect.qual.UIPackage;
import org.springframework.security.core.parameters.P;

@Mapper
public interface UserPointsMapper {

    @Insert("""
            INSERT INTO user_points (user_id, balance, version, updated_at)
            VALUES (#{userId}, #{balance}, #{version}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert (UserPoints userPoints);

    @Select("SELECT * FROM user_points WHERE user_id = #{userId}")
    UserPoints selectByUserId(Long userId);

    /**
     * 增加积分
     */
    @Update("""
            UPDATE user_points SET balance + #{amount}, version = version + 1, uddated_at = NOW()
            WHERE user_id = #{userId} AND version = #{version}
            """)
    int increaseBalance(@Param("userId") Long userId,
                        @Param("amount") Integer amount,
                        @Param("version") Integer version);


    /**
     * 扣减积分
     */
    @Update("""
            UPDATE user_points SET balance = balance - #{amount}, version = version + 1, updated_at = NOW()
            WHERE user_id = #{userId} AND version = #{version} AND balance >= #{amount}
            """)
    int decreaseBalance(@Param("userId") Long userId,
                        @Param("amount") Integer amount,
                        @Param("version") Integer version);
}