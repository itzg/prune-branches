package io.github.itzg.ghrel;

class Assets {
    public final Asset zipFile;
    public final Asset tarFile;

    Assets(Asset zipFile, Asset tarFile) {
        this.zipFile = zipFile;
        this.tarFile = tarFile;
    }

    Asset getZipFile() {
        return zipFile;
    }

    @SuppressWarnings("unused")
    Asset getTarFile() {
        return tarFile;
    }

    @Override
    String toString() {
        return "Assets{" +
            "zipFile=" + zipFile +
            ", tarFile=" + tarFile +
            '}';
    }

    static class Asset {
        final String downloadUrl;
        final String sha256;

        Asset(String downloadUrl, String sha256) {
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
        }

        String getDownloadUrl() {
            return downloadUrl;
        }

        String getSha256() {
            return sha256;
        }
    }

}
