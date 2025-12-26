package org.example.backend.service;

import org.example.backend.model.AnswerImage;
import org.example.backend.repository.AnswerImageRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnswerImageService {

    private final AnswerImageRepository answerImageRepository;

    public AnswerImageService(AnswerImageRepository answerImageRepository) {
        this.answerImageRepository = answerImageRepository;

    }

    public AnswerImage saveImage(Long answerId, String imagePath) {
        AnswerImage image = new AnswerImage();
        image.setAnswerId(answerId);
        image.setImagePath(imagePath);
        image.setCreatedAt(LocalDateTime.now());
        return answerImageRepository.save(image);
    }

    public List<AnswerImage> getImagesByAnswerId(Long answerId) {
        return answerImageRepository.findByAnswerId(answerId);
    }

    public void deleteImagesByAnswerId(Long answerId) {
        answerImageRepository.deleteByAnswerId(answerId);
    }
    public  void deleteImagesByImageId(Long imageId){
        answerImageRepository.deleteByImageId(imageId);
    }
}