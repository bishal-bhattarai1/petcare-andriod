package com.example.petcare.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface PetDao {
    @Insert
    long insertPet(PetEntity pet);

    @Update
    void updatePet(PetEntity pet);

    @Delete
    void deletePet(PetEntity pet);

    @Query("SELECT * FROM pets WHERE ownerEmail = :ownerEmail")
    List<PetEntity> getPetsByOwner(String ownerEmail);

    @Query("SELECT * FROM pets WHERE id = :id LIMIT 1")
    PetEntity getPetById(int id);
}