package com.example.petcare.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface TaskDao {
    @Insert
    long insertTask(TaskEntity task);

    @Update
    void updateTask(TaskEntity task);

    @Delete
    void deleteTask(TaskEntity task);

    @Query("SELECT * FROM tasks WHERE ownerEmail = :ownerEmail")
    List<TaskEntity> getTasksByOwner(String ownerEmail);

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    TaskEntity getTaskById(int id);

    @Query("UPDATE tasks SET isCompleted = :isCompleted WHERE id = :id")
    void updateTaskCompletion(int id, boolean isCompleted);
}