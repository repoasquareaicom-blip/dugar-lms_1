package dugar_lms_api.common.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public abstract class BaseEntity {

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}