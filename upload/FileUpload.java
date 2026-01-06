package upload;

public class FileUpload {
    private byte[] content;
    private String filename;
    private String contentType;
    private long size;
    private String savedPath;

    public FileUpload() {}

    public FileUpload(byte[] content, String filename, String contentType, long size) {
        this.content = content;
        this.filename = filename;
        this.contentType = contentType;
        this.size = size;
    }

    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getSavedPath() { return savedPath; }
    public void setSavedPath(String savedPath) { this.savedPath = savedPath; }
}
