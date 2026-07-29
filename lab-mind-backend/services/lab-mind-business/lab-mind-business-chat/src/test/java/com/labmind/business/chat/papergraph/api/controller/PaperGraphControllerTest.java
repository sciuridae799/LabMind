package com.labmind.business.chat.papergraph.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

class PaperGraphControllerTest {

    @Test
    void everyNamedWebArgumentDeclaresItsRouteName() {
        for (Method method : PaperGraphController.class.getDeclaredMethods()) {
            for (Parameter parameter : method.getParameters()) {
                PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
                if (pathVariable != null) {
                    assertThat(pathVariable.value())
                            .as("%s path variable", method.getName())
                            .isNotBlank();
                }
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                if (requestParam != null) {
                    assertThat(requestParam.name())
                            .as("%s request parameter", method.getName())
                            .isNotBlank();
                }
                RequestPart requestPart = parameter.getAnnotation(RequestPart.class);
                if (requestPart != null) {
                    assertThat(requestPart.value())
                            .as("%s request part", method.getName())
                            .isNotBlank();
                }
            }
        }
    }

    @Test
    void oversizedPdfIsRejectedBeforeGatewayInvocation() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        PaperGraphController controller = new PaperGraphController(null, null);

        assertThatThrownBy(() -> controller.uploadDocument(UUID.randomUUID(), file))
                .isInstanceOf(MaxUploadSizeExceededException.class);
    }
}
