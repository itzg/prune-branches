package io.github.itzg.ghrel;

public class Assets {
    public final Asset zipFile;
    public final Asset tarFile;

    public Assets(Asset zipFile, Asset tarFile) {
        this.zipFile = zipFile;
        this.tarFile = tarFile;
    }

    public Asset getZipFile() {
        return zipFile;
    }

    @SuppressWarnings("unused")
    public Asset getTarFile() {
        return tarFile;
    }

    @Override
    public String toString() {
        return "Assets{" +
            "zipFile=" + zipFile +
            ", tarFile=" + tarFile +
            '}';
    }

    public static class Asset {
        final String downloadUrl;
        final String sha256;

        public Asset(String downloadUrl, String sha256) {
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public String getSha256() {
            return sha256;
        }
    }

}
