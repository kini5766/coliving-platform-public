package com.coliving.document.grpc;

import com.coliving.document.form.repository.DocumentFormRepository;
import com.coliving.platform.grpc.document.DocumentServiceGrpc;
import com.coliving.platform.grpc.document.GetDocumentFormRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class DocumentGrpcService extends DocumentServiceGrpc.DocumentServiceImplBase {

    private final DocumentFormRepository formRepository;

    public DocumentGrpcService(DocumentFormRepository formRepository) {
        this.formRepository = formRepository;
    }

    @Override
    public void getDocumentForm(GetDocumentFormRequest request,
                                StreamObserver<com.coliving.platform.grpc.document.DocumentForm> responseObserver) {
        formRepository.findById(request.getTemplateId()).ifPresentOrElse(
                form -> {
                    responseObserver.onNext(com.coliving.platform.grpc.document.DocumentForm.newBuilder()
                            .setTemplateId(form.getId())
                            .setLanguage(form.getLanguage())
                            .setContent(form.getContent())
                            .build());
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(Status.NOT_FOUND
                        .withDescription("존재하지 않는 계약서 양식입니다. templateId=" + request.getTemplateId())
                        .asRuntimeException())
        );
    }
}