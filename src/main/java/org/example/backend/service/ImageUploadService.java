package org.example.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class ImageUploadService {

    @Value("${upload.path}")
    private String uploadPath;

    /**
     * 上传图片到指定子目录
     * @param fileName 图片名称
     * @param subDirectory 子目录名称（如 "questions"、"avatars"）
     * @return 图片访问URL
     * @throws IOException 文件操作异常
     */
    public String saveImage(String tempDirectory, String fileName, String subDirectory) throws IOException {
        // 构建临时文件的完整路径
        Path tempFilePath = Paths.get(tempDirectory, fileName);

        // 检查临时文件是否存在
        if (!Files.exists(tempFilePath)) {
            throw new IOException("临时文件 " + tempFilePath + " 不存在。");
        }

        // 构建目标目录的路径
        Path targetDirectory = Paths.get(subDirectory);

        // 检查目标目录是否存在，如果不存在则创建
        if (!Files.exists(targetDirectory)) {
            Files.createDirectories(targetDirectory);
        }

        // 构建目标文件的完整路径
        Path targetFilePath = targetDirectory.resolve(fileName);

        // 移动文件到目标目录
        Files.move(tempFilePath, targetFilePath, StandardCopyOption.REPLACE_EXISTING);

        // 返回移动后文件的新地址
        return targetFilePath.toString();
    }


    public String uploadImage(MultipartFile file, String subDirectory) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件不能为空");
        }

        // 构建完整上传路径（主目录 + 子目录）
        Path directory = Paths.get(uploadPath, subDirectory);
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }

        // 生成唯一文件名（UUID + 原始文件名）
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path targetPath = directory.resolve(fileName);

        // 保存文件并确保替换已存在的同名文件
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // 返回访问URL（根据实际部署调整）
        return "/uploads/" + subDirectory + "/" + fileName;
    }

    public String uploadTempImage(MultipartFile file, String subDirectory) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件不能为空");
        }

        // 构建完整上传路径（主目录 + 子目录）
        Path directory = Paths.get(uploadPath, subDirectory);
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }

        // 生成唯一文件名（UUID + 原始文件名）
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path targetPath = directory.resolve(fileName);

        // 保存文件并确保替换已存在的同名文件
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // 返回访问URL（根据实际部署调整）
        return "/uploads/" + subDirectory + "/" + fileName;
    }

}