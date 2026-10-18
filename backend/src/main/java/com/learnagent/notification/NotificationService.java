package com.learnagent.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
  private final NotificationRepository repository;
  public NotificationService(NotificationRepository repository) { this.repository = repository; }
  @Transactional
  public Notification create(NotificationType type, String title, String body, String actionUrl,
                             String referenceType, UUID referenceId, String dedupKey) {
    return repository.findByDedupKey(dedupKey).orElseGet(() -> repository.save(
        new Notification(type, title, body, actionUrl, referenceType, referenceId, dedupKey)));
  }
  @Transactional(readOnly = true) public List<Notification> list(boolean unreadOnly) {
    return unreadOnly ? repository.findByReadAtIsNullOrderByCreatedAtDesc() : repository.findAllByOrderByCreatedAtDesc();
  }
  @Transactional(readOnly = true) public long unreadCount() { return repository.countByReadAtIsNull(); }
  @Transactional public Notification markRead(UUID id) { Notification value = required(id); value.markRead(); return value; }
  @Transactional public void markAllRead() { repository.findByReadAtIsNullOrderByCreatedAtDesc().forEach(Notification::markRead); }
  private Notification required(UUID id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found.")); }
}
