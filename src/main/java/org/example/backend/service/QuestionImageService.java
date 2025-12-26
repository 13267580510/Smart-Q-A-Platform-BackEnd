package org.example.backend.service;

import jakarta.transaction.Transactional;
import org.example.backend.model.QuestionImage;
import org.example.backend.repository.QuestionImageRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class QuestionImageService {

    private final QuestionImageRepository questionImageRepository;

    public QuestionImageService(QuestionImageRepository questionImageRepository) {
        this.questionImageRepository = questionImageRepository;
    }

    public QuestionImage saveImage(Long questionId, String imagePath) {
        QuestionImage image;
            image = new QuestionImage();
            image.setQuestionId(questionId);
            image.setImagePath(imagePath);
            image.setCreatedAt(LocalDateTime.now());
        return questionImageRepository.save(image);
    }

    public List<QuestionImage> getImagesByQuestionId(Long questionId) {
        return questionImageRepository.findByQuestionId(questionId);
    }
    @Transactional
    public Map<String, Object> deleteImageById(Long imageId, Long questionId) {
        Map<String, Object> resultMap = new HashMap<>();
        // 先查询图片

        QuestionImage questionImage = questionImageRepository.findById(imageId)
                .orElse(null);

        if (questionImage == null) {
            resultMap.put("success", false);
            resultMap.put("message", "不存在此图片，删除失败");
            return resultMap;
        }

        // 检查图片所属的 questionId 是否与传入的 questionId 一致
        if (!questionImage.getQuestionId().equals(questionId)) {
            resultMap.put("success", false);
            resultMap.put("message", "无权删除，图片所属的问题 ID 与传入的问题 ID 不一致");
            return resultMap;
        }
        System.out.println("图片删除成功");
        // 如果通过上述检查，执行删除操作
        questionImageRepository.deleteById(imageId);
        resultMap.put("success", true);
        resultMap.put("message", "图片删除成功");
        return resultMap;
    }

    public void deleteImagesByQuestionId(Long questionId) {
        questionImageRepository.deleteByQuestionId(questionId);
    }
}