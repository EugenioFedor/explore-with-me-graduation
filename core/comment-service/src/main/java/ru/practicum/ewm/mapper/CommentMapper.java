package ru.practicum.ewm.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.dto.NewCommentDto;
import ru.practicum.ewm.dto.UpdateCommentDto;
import ru.practicum.ewm.model.Comment;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CommentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "eventId", ignore = true)
    @Mapping(target = "authorId", ignore = true)
    Comment toEntity(NewCommentDto newCommentDto);

    @Mapping(source = "eventId", target = "eventId")
    @Mapping(target = "author", ignore = true)
    CommentDto toDto(Comment comment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "eventId", ignore = true)
    @Mapping(target = "authorId", ignore = true)
    Comment toEntity(UpdateCommentDto updateCommentDto);
}
