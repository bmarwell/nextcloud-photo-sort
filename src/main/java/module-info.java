module de.bmarwell.home.nextcloud.photo.sort {
    requires metadata.extractor;
    requires org.apache.commons.codec;
    requires info.picocli;
    requires static org.jspecify;

    exports de.bmarwell.home.nextcloud.photo.sort;

    opens de.bmarwell.home.nextcloud.photo.sort to
            info.picocli;
}
