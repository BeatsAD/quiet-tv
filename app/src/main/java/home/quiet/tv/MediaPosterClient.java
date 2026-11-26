package home.quiet.tv;

import java.io.IOException;
import java.util.List;

interface MediaPosterClient {
    List<FeiniuMediaClient.Item> libraries(FeiniuMediaClient.Session session) throws IOException;
    List<FeiniuMediaClient.Item> movies(FeiniuMediaClient.Session session, String library, int offset) throws IOException;
    byte[] poster(FeiniuMediaClient.Session session, FeiniuMediaClient.Item movie) throws IOException;
}
