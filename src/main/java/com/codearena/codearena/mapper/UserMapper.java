package com.codearena.codearena.mapper;

import com.codearena.codearena.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findByUsername(String username);
    int insertUser(User user);
    int updatePasswordHashIfUnchanged(@Param("userId") Long userId,
                                      @Param("expectedPassword") String expectedPassword,
                                      @Param("passwordHash") String passwordHash);

}
