package com.example.petcare.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pets")
public class PetEntity {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String ownerEmail;
    private String name;
    private String breed;
    private String age;
    private String weight;
    private String diet;
    private String allergies;
    private String vaccinationHistory;
    private String photoPath;

    public PetEntity(String ownerEmail, String name, String breed, String age, String weight, String diet, String allergies, String vaccinationHistory, String photoPath) {
        this.ownerEmail = ownerEmail;
        this.name = name;
        this.breed = breed;
        this.age = age;
        this.weight = weight;
        this.diet = diet;
        this.allergies = allergies;
        this.vaccinationHistory = vaccinationHistory;
        this.photoPath = photoPath;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }

    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public String getDiet() { return diet; }
    public void setDiet(String diet) { this.diet = diet; }

    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }

    public String getVaccinationHistory() { return vaccinationHistory; }
    public void setVaccinationHistory(String vaccinationHistory) { this.vaccinationHistory = vaccinationHistory; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }
}