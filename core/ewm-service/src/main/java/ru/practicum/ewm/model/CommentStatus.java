package ru.practicum.ewm.model;

public enum CommentStatus {
    PENDING,    // ожидает модерации
    PUBLISHED,  // опубликован (виден всем)
    REJECTED,   // отклонён администратором
    DELETED;     // удалён автором (мягкое удаление)

    public static CommentStatus from(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return CommentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown comment status: " + status);
        }
    }
}
