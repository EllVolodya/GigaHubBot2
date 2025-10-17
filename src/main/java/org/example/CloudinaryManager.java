package org.example;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class CloudinaryManager {
    private static final Cloudinary cloudinary;

    static {
        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", System.getenv("GigaHubBot"),
                "api_key", System.getenv("673113637171148"),
                "api_secret", System.getenv("kcKYT7Ju_1g_x-00oxzuhBGs6Ps")
        ));
    }

    // Завантаження фото
    public static String uploadImage(File file, String folder) throws IOException {
        Map uploadResult = cloudinary.uploader().upload(file, ObjectUtils.asMap(
                "folder", folder,
                "overwrite", true
        ));
        return (String) uploadResult.get("secure_url");
    }

    // Видалення фото
    public static void deleteImage(String imageUrl) {
        try {
            if (imageUrl == null || !imageUrl.contains("/")) return;

            // Отримуємо public_id з URL Cloudinary
            String publicId = imageUrl.substring(imageUrl.indexOf("/upload/") + 8);
            publicId = publicId.replaceAll("\\.[^.]+$", ""); // прибираємо .jpg/.png

            // Видаляємо
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            System.out.println("[CLOUDINARY] Видалено фото: " + publicId);
        } catch (Exception e) {
            System.err.println("[CLOUDINARY] Не вдалося видалити фото: " + e.getMessage());
        }
    }
}