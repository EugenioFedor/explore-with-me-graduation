package ru.practicum.ewm.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.practicum.ewm.dto.CategoryDto;
import ru.practicum.ewm.dto.NewCategoryDto;
import ru.practicum.ewm.model.Category;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CategoryMapper {

    Category toEntity(NewCategoryDto newCategoryDto);

    CategoryDto toDto(Category category);

    Category updateFromDto(CategoryDto categoryDto);
}