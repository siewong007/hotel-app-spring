package com.hotelapp.ekyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.ekyc.EkycModels.EkycSubmissionRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract tests for the pure helpers ported from modules/ekyc/validation.rs. */
class EkycPortalContractTest {

    @Test
    void documentTypeKeepsOnlyAlphanumericDashUnderscore() {
        assertThat(EkycPortalService.sanitizeDocumentType("passport_front"))
                .isEqualTo("passport_front");
        assertThat(EkycPortalService.sanitizeDocumentType("my doc!.type"))
                .isEqualTo("mydoctype");
        assertThat(EkycPortalService.sanitizeDocumentType("a".repeat(60)))
                .hasSize(40);
    }

    @Test
    void documentTypeRejectsEmptyAfterSanitize() {
        assertThatThrownBy(() -> EkycPortalService.sanitizeDocumentType("!!!"))
                .isInstanceOf(ApiError.class)
                .hasMessageContaining("Invalid document type");
    }

    @Test
    void imageExtensionSniffsMagicBytes() {
        assertThat(EkycPortalService.imageExtension(
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x10}))
                .isEqualTo("jpg");
        assertThat(EkycPortalService.imageExtension(new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}))
                .isEqualTo("png");
        byte[] webp = "RIFFxxxxWEBP".getBytes();
        assertThat(EkycPortalService.imageExtension(webp)).isEqualTo("webp");
        assertThat(EkycPortalService.imageExtension("not an image".getBytes())).isNull();
    }

    @Test
    void imageBytesEnforceSizeAndSignature() {
        assertThatThrownBy(() -> EkycPortalService.validateImageBytes(new byte[0]))
                .hasMessageContaining("between 1 byte and 10MB");
        assertThatThrownBy(() -> EkycPortalService.validateImageBytes(
                new byte[(int) EkycPortalService.MAX_EKYC_IMAGE_BYTES + 1]))
                .hasMessageContaining("between 1 byte and 10MB");
        assertThatThrownBy(() -> EkycPortalService.validateImageBytes("text".getBytes()))
                .hasMessageContaining("JPEG, PNG, or WebP");
    }

    @Test
    void existingPathRequiresPrefixAndUserNamespace() {
        assertThatThrownBy(() -> EkycPortalService.validateExistingEkycPath(
                "uploads/pub.jpg", 7))
                .hasMessageContaining("Invalid eKYC image reference");
        assertThatThrownBy(() -> EkycPortalService.validateExistingEkycPath(
                "private_uploads/ekyc/8_doc_1_x.jpg", 7))
                .hasMessageContaining("Invalid eKYC image reference");
        assertThatThrownBy(() -> EkycPortalService.validateExistingEkycPath(
                "private_uploads/ekyc/../escape.jpg", 7))
                .hasMessageContaining("Invalid eKYC image reference");
    }

    @Test
    void existingPathRejectsMissingFile() {
        assertThatThrownBy(() -> EkycPortalService.validateExistingEkycPath(
                "private_uploads/ekyc/7_doc_0_" + java.util.UUID.randomUUID() + ".jpg", 7))
                .hasMessageContaining("does not exist");
    }

    @Test
    void expiryMustBeStrictlyFuture() {
        LocalDate today = LocalDate.of(2026, 9, 12);
        assertThatThrownBy(() -> EkycPortalService.validateDateStrings(
                "1990-01-01", "2026-09-12", null, today))
                .hasMessageContaining("must be in the future");
        LocalDate[] ok = EkycPortalService.validateDateStrings(
                "1990-01-01", "2027-01-01", "2020-01-01", today);
        assertThat(ok[0]).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(ok[1]).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(ok[2]).isEqualTo(LocalDate.of(2020, 1, 1));
    }

    @Test
    void dateParsingIsStrictIso() {
        assertThatThrownBy(() -> EkycPortalService.validateDateStrings(
                "01/01/1990", "2030-01-01", null, LocalDate.now()))
                .hasMessageContaining("Invalid date of birth");
        assertThatThrownBy(() -> EkycPortalService.validateDateStrings(
                "1990-01-01", "not-a-date", null, LocalDate.now()))
                .hasMessageContaining("Invalid ID expiry date");
        assertThatThrownBy(() -> EkycPortalService.validateDateStrings(
                "1990-01-01", "2030-01-01", "bad", LocalDate.now()))
                .hasMessageContaining("Invalid ID issue date");
    }

    @Test
    void fieldLengthsMirrorColumnWidths() {
        EkycSubmissionRequest base = request(null);
        EkycPortalService.validateSubmissionFieldLengths(base);
        EkycSubmissionRequest longName = new EkycSubmissionRequest(
                "a", "b", null, "passport", "X1", "x".repeat(256),
                "1990-01-01", null, null, "2030-01-01", null, null, null, null,
                null, null, List.of());
        assertThatThrownBy(() -> EkycPortalService.validateSubmissionFieldLengths(longName))
                .hasMessageContaining("full_name must be 255");
    }

    @Test
    void blankSanitizedNameAndIdNumberAreRejected() {
        EkycSubmissionRequest blankName = new EkycSubmissionRequest(
                "a", "b", null, "passport", "X1", "",
                "1990-01-01", null, null, "2030-01-01", null, null, null, null,
                null, null, List.of());
        assertThatThrownBy(() -> EkycPortalService.validateSubmissionFieldLengths(blankName))
                .hasMessageContaining("full_name is required");
        EkycSubmissionRequest blankId = new EkycSubmissionRequest(
                "a", "b", null, "passport", "  ", "Name",
                "1990-01-01", null, null, "2030-01-01", null, null, null, null,
                null, null, List.of());
        assertThatThrownBy(() -> EkycPortalService.validateSubmissionFieldLengths(blankId))
                .hasMessageContaining("id_number is required");
    }

    @Test
    void phoneWithoutDigitsIsRejected() {
        EkycSubmissionRequest badPhone = new EkycSubmissionRequest(
                "a", "b", null, "passport", "X1", "Name",
                "1990-01-01", null, null, "2030-01-01", null, null, null,
                "(+)-()-", null, null, List.of());
        assertThatThrownBy(() -> EkycPortalService.validateSubmissionFieldLengths(badPhone))
                .hasMessageContaining("at least one digit");
    }

    @Test
    void filenameEmbedsUserIdAndExtension() {
        String name = EkycPortalService.buildEkycFilename(42, "id_front", "jpg");
        assertThat(name).startsWith("42_id_front_").endsWith(".jpg");
    }

    private static EkycSubmissionRequest request(String fullName) {
        return new EkycSubmissionRequest(
                "a", "b", null, "passport", "X1", fullName == null ? "A Guest" : fullName,
                "1990-01-01", null, null, "2030-01-01", null, null, null, null,
                null, null, List.of());
    }
}
