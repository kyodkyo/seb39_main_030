package debateProgram.server.declaration.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AllDeclarationResponseDto {

    private int declarationCode;
    private String declarationReason;
    private int userCode;
    private String nickname;
    private String profileImg;

}
