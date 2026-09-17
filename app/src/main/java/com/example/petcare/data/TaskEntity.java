package com.example.petcare.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class TaskEntity {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String ownerEmail;
    private String petName;
    private String description;
    private String feedingTime;
    private String medicationSchedule;
    private String groomingInstructions;
    private String appointmentDateTime;
    private boolean isCompleted;

    public TaskEntity(String ownerEmail, String petName, String description, String feedingTime, String medicationSchedule, String groomingInstructions, String appointmentDateTime, boolean isCompleted) {
        this.ownerEmail = ownerEmail;
        this.petName = petName;
        this.description = description;
        this.feedingTime = feedingTime;
        this.medicationSchedule = medicationSchedule;
        this.groomingInstructions = groomingInstructions;
        this.appointmentDateTime = appointmentDateTime;
        this.isCompleted = isCompleted;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }

    public String getPetName() { return petName; }
    public void setPetName(String petName) { this.petName = petName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFeedingTime() { return feedingTime; }
    public void setFeedingTime(String feedingTime) { this.feedingTime = feedingTime; }

    public String getMedicationSchedule() { return medicationSchedule; }
    public void setMedicationSchedule(String medicationSchedule) { this.medicationSchedule = medicationSchedule; }

    public String getGroomingInstructions() { return groomingInstructions; }
    public void setGroomingInstructions(String groomingInstructions) { this.groomingInstructions = groomingInstructions; }

    public String getAppointmentDateTime() { return appointmentDateTime; }
    public void setAppointmentDateTime(String appointmentDateTime) { this.appointmentDateTime = appointmentDateTime; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }
}