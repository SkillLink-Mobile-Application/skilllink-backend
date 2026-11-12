
package com.skilllink.controller;

import com.skilllink.entity.Category;
import com.skilllink.service.CategoryService;
import com.skilllink.service.CategoryImageService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryImageService categoryImageService;

    public CategoryController(
            CategoryService categoryService,
            CategoryImageService categoryImageService) {
        this.categoryService = categoryService;
        this.categoryImageService = categoryImageService;
    }

    // CUSTOMER & PROVIDER: Get active categories
    @GetMapping(
            value = "/api/categories",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<Category>> getActiveCategories() {
        return ResponseEntity.ok(
                categoryService.getActiveCategories()
        );
    }

    // ADMIN: Get all categories
    @GetMapping("/api/admin/categories")
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(
                categoryService.getAllCategories()
        );
    }

    // ADMIN: Add category
    @PostMapping("/api/admin/categories")
    public ResponseEntity<?> addCategory(
            @RequestBody Category category) {

        try {
            Category saved = categoryService.addCategory(category);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(saved);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ADMIN: Edit category
    @PutMapping("/api/admin/categories/{id}")
    public ResponseEntity<?> updateCategory(
            @PathVariable Long id,
            @RequestBody Category category) {

        try {
            return ResponseEntity.ok(
                    categoryService.updateCategory(id, category)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ADMIN: Upload category image to Cloudinary
    @PostMapping(
            value = "/api/admin/categories/upload-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadCategoryImage(
            @RequestParam("file") MultipartFile file) {

        try {
            String imageUrl = categoryImageService.uploadImage(file);

            return ResponseEntity.ok(
                    Map.of("imageUrl", imageUrl)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Image upload failed"));
        }
    }

    // ADMIN: Activate / Deactivate category
    @PatchMapping("/api/admin/categories/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> request) {

        try {
            Boolean active = request.get("active");

            if (active == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "message",
                                "Active status is required"
                        ));
            }

            return ResponseEntity.ok(
                    categoryService.updateStatus(id, active)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
