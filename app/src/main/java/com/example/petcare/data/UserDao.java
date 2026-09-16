package com.example.petcare.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface UserDao {
    @Insert
    long insertUser(UserEntity user);

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    UserEntity getUserByEmail(String email);

    @Query("UPDATE users SET password = :newPassword WHERE email = :email")
    void updatePassword(String email, String newPassword);

    @Query("SELECT * FROM users")
    java.util.List<UserEntity> getAllUsers();
}