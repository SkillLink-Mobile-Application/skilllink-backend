
package com.skilllink.service;

import com.skilllink.entity.Category;
import com.skilllink.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public List<Category> getActiveCategories() {
        return categoryRepository.findByActiveTrue();
    }

    @Transactional
    public Category addCategory(Category category) {

        if (category.getName() == null ||
                category.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }

        String name = category.getName().trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Category already exists");
        }

        category.setName(name);
        category.setActive(true);

        // Default to icon when no image is provided
        if (category.getImageUrl() != null &&
                !category.getImageUrl().trim().isEmpty()) {
            category.setIcon(null);
        }

        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(Long id, Category updatedCategory) {

        Category existing = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Category not found"));

        String name = updatedCategory.getName();

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }

        name = name.trim();

        if (categoryRepository.findByNameIgnoreCase(name)
                .filter(category -> !category.getId().equals(id))
                .isPresent()) {
            throw new IllegalArgumentException("Category already exists");
        }

        existing.setName(name);
        existing.setDescription(updatedCategory.getDescription());

        String imageUrl = updatedCategory.getImageUrl();

        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            existing.setImageUrl(imageUrl.trim());
            existing.setIcon(null);
        } else {
            existing.setImageUrl(null);
            existing.setIcon(updatedCategory.getIcon());
        }

        return categoryRepository.save(existing);
    }

    @Transactional
    public Category updateStatus(Long id, boolean active) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Category not found"));

        category.setActive(active);

        return categoryRepository.save(category);
    }
}
