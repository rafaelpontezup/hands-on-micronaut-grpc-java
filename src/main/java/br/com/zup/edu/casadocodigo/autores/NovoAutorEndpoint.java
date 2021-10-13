package br.com.zup.edu.casadocodigo.autores;

import br.com.zup.edu.CasadocodigoGrpcServiceGrpc;
import br.com.zup.edu.NovoAutorRequest;
import br.com.zup.edu.NovoAutorResponse;
import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import com.google.rpc.BadRequest;
import com.google.rpc.Code;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintViolationException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

@Singleton
public class NovoAutorEndpoint extends CasadocodigoGrpcServiceGrpc.CasadocodigoGrpcServiceImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(NovoAutorEndpoint.class);

    @Inject
    private AutorRepository repository;

    @Override
    public void cadastra(NovoAutorRequest request, StreamObserver<NovoAutorResponse> responseObserver) {

        LOGGER.info("request={}", request);

        try {

            if (repository.existsByEmail(request.getEmail())) {
                responseObserver.onError(Status.ALREADY_EXISTS
                                            .withDescription("autor ja existente")
                                            .asRuntimeException());
                return;
            }

            Autor autor = toModel(request); // toString(), ou toJson() ou toXml()
            repository.save(autor); // hibernate dispara a Bean Validation

            Instant instant = autor.getCriadoEm().atZone(ZoneId.of("UTC-0")).toInstant();
            Timestamp now = Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build();

            responseObserver.onNext(NovoAutorResponse.newBuilder()
                                        .setId(autor.getId())
                                        .setCriadoEm(now)
                                        .build());
            responseObserver.onCompleted();

        } catch (ConstraintViolationException e) {
            e.printStackTrace();

            List<BadRequest.FieldViolation> violations = e.getConstraintViolations().stream().map(v -> {
                return BadRequest.FieldViolation.newBuilder()
                            .setField(v.getPropertyPath().toString())
                            .setDescription(v.getMessage())
                            .build();
            }).collect(toList());

            BadRequest badRequest = BadRequest.newBuilder()
                    .addAllFieldViolations(violations)
                    .build();

            com.google.rpc.Status statusProto = com.google.rpc.Status
                                                    .newBuilder()
                                                    .setCode(Code.INVALID_ARGUMENT.getNumber())
                                                    .setMessage("parametros de entrada invalidos")
                                                    .addDetails(Any.pack(badRequest))
                                                    .build();

            LOGGER.info("statusProto={}", statusProto);

            StatusException exception = StatusProto.toStatusException(statusProto);
            responseObserver.onError(exception);
        }
    }

    private Autor toModel(NovoAutorRequest request) {
        return new Autor(request.getNome(), request.getEmail(), request.getDescricao());
    }

}
