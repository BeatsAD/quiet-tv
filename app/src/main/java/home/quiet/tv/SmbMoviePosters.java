package home.quiet.tv;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mserref.NtStatus;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.auth.NtlmAuthenticator;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.Directory;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Opens a single authorized share and reads sidecar artwork using SMB2 or SMB3. */
final class SmbMoviePosters {
    private static final int MAX_ENTRIES = 4000;
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;

    static final class Folder {
        final String address, host, share, path;
        final int port;

        private Folder(String address, String host, int port, String share, String path) {
            this.address = address;
            this.host = host;
            this.port = port;
            this.share = share;
            this.path = path;
        }
    }

    static Folder folder(String input) throws InvalidFolder {
        try {
            String address = input.trim();
            if (address.startsWith("\\\\")) address = "smb://" + address.substring(2).replace('\\', '/');
            URI uri = new URI(address.replace(" ", "%20"));
            int port = uri.getPort();
            if (!"smb".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || port == 0 || port > 65535) throw new InvalidFolder();
            List<String> parts = new ArrayList<>();
            for (String part : uri.getPath().split("/")) {
                if (part.isEmpty()) continue;
                if (!validName(part)) throw new InvalidFolder();
                parts.add(part);
            }
            if (parts.isEmpty()) throw new InvalidFolder();
            String share = parts.remove(0);
            StringBuilder smbPath = new StringBuilder(), urlPath = new StringBuilder("/").append(share);
            for (String part : parts) {
                if (smbPath.length() > 0) smbPath.append('\\');
                smbPath.append(part);
                urlPath.append('/').append(part);
            }
            String canonical = new URI("smb", null, uri.getHost(), port, urlPath.toString(), null, null).toString();
            return new Folder(canonical, uri.getHost(), port == -1 ? 445 : port, share, smbPath.toString());
        } catch (URISyntaxException e) {
            throw new InvalidFolder();
        }
    }

    interface Read<T> {
        T read(DiskShare share) throws IOException;
    }

    static <T> T read(Folder folder, String username, String password, Read<T> reader) throws IOException {
        String[] account = username.split("\\\\", 2);
        AuthenticationContext auth = username.isEmpty() ? AuthenticationContext.anonymous()
                : new AuthenticationContext(account[account.length - 1], password.toCharArray(),
                account.length == 2 ? account[0] : "");
        SmbConfig config = SmbConfig.builder()
                .withAuthenticators(Collections.singletonList(new NtlmAuthenticator.Factory()))
                .withTimeout(8, TimeUnit.SECONDS).withSoTimeout(10, TimeUnit.SECONDS).build();
        try (SMBClient client = new SMBClient(config);
             Connection connection = client.connect(folder.host, folder.port);
             Session session = connection.authenticate(auth)) {
            try (DiskShare share = (DiskShare) session.connectShare(folder.share)) {
                return reader.read(share);
            }
        } catch (SMBApiException e) {
            NtStatus status = e.getStatus();
            if (status == NtStatus.STATUS_ACCESS_DENIED || status == NtStatus.STATUS_LOGON_FAILURE)
                throw new FeiniuMediaClient.Unauthorized();
            if (status == NtStatus.STATUS_BAD_NETWORK_NAME || status == NtStatus.STATUS_NO_SUCH_FILE
                    || status == NtStatus.STATUS_OBJECT_NAME_NOT_FOUND
                    || status == NtStatus.STATUS_OBJECT_PATH_NOT_FOUND || status == NtStatus.STATUS_NOT_A_DIRECTORY)
                throw new InvalidFolder();
            throw new IOException("SMB read failed", e);
        }
    }

    static List<String> posters(DiskShare share, String root, int limit) {
        List<String> found = new ArrayList<>();
        ArrayDeque<String> directories = new ArrayDeque<>();
        directories.add(root);
        int visited = 0;
        while (!directories.isEmpty() && found.size() < limit && visited < MAX_ENTRIES) {
            String path = directories.removeFirst();
            String primary = "";
            int best = Integer.MAX_VALUE;
            List<String> named = new ArrayList<>();
            try (Directory directory = share.openDirectory(path, EnumSet.of(AccessMask.FILE_LIST_DIRECTORY),
                    null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)) {
                Iterator<FileIdBothDirectoryInformation> entries = directory.iterator();
                while (visited < MAX_ENTRIES && entries.hasNext()) {
                    FileIdBothDirectoryInformation entry = entries.next();
                    visited++;
                    String name = entry.getFileName();
                    long attributes = entry.getFileAttributes();
                    if (!validName(name) || (attributes & FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.getValue()) != 0) continue;
                    String child = path.isEmpty() ? name : path + "\\" + name;
                    if ((attributes & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0) {
                        if (!name.startsWith(".")) directories.addLast(child);
                        continue;
                    }
                    if (entry.getEndOfFile() > MAX_IMAGE_BYTES) continue;
                    int priority = priority(name);
                    if (priority == 3) {
                        if (named.size() < limit) named.add(child);
                    } else if (priority < best || (priority == best && child.compareTo(primary) < 0)) {
                        primary = child;
                        best = priority;
                    }
                }
            }
            if (!primary.isEmpty()) found.add(primary);
            else {
                Collections.sort(named);
                found.addAll(named.subList(0, Math.min(named.size(), limit - found.size())));
            }
        }
        return found;
    }

    static byte[] poster(DiskShare share, String path) throws IOException {
        try (File file = share.openFile(path, EnumSet.of(AccessMask.FILE_READ_DATA, AccessMask.FILE_READ_ATTRIBUTES),
                null, SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE, SMB2CreateOptions.FILE_OPEN_REPARSE_POINT))) {
            long size = file.getLength();
            if (size <= 0 || size > MAX_IMAGE_BYTES) return null;
            byte[] bytes = new byte[(int) size];
            int offset = 0;
            while (offset < bytes.length) {
                int read = file.read(bytes, offset, offset, Math.min(64 * 1024, bytes.length - offset));
                if (read <= 0) return null;
                offset += read;
            }
            return bytes;
        } catch (SMBApiException e) {
            if (e.getStatus() == NtStatus.STATUS_NO_SUCH_FILE || e.getStatus() == NtStatus.STATUS_OBJECT_NAME_NOT_FOUND
                    || e.getStatus() == NtStatus.STATUS_OBJECT_PATH_NOT_FOUND) return null;
            throw e;
        }
    }

    private static int priority(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 1) return Integer.MAX_VALUE;
        String ext = lower.substring(dot + 1);
        if (!ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png") && !ext.equals("webp")) return Integer.MAX_VALUE;
        String stem = lower.substring(0, dot);
        if (stem.equals("poster")) return 0;
        if (stem.equals("folder")) return 1;
        if (stem.equals("cover")) return 2;
        return stem.endsWith("-poster") ? 3 : Integer.MAX_VALUE;
    }

    private static boolean validName(String part) {
        if (part.isEmpty() || part.equals(".") || part.equals("..") || part.indexOf('/') >= 0 || part.indexOf('\\') >= 0)
            return false;
        for (int i = 0; i < part.length(); i++) if (Character.isISOControl(part.charAt(i))) return false;
        return true;
    }

    static final class InvalidFolder extends IOException {}
}
