package com.learnagent.notification;
import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationRepository extends JpaRepository<Notification,UUID>{List<Notification>findAllByOrderByCreatedAtDesc();List<Notification>findByReadAtIsNullOrderByCreatedAtDesc();long countByReadAtIsNull();Optional<Notification>findByDedupKey(String key);}
