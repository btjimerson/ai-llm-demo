package dev.snbv2.ai;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * POJO to store a chat prompt.
 * 
 * @author Brian Jimerson
 */
@Getter
@Setter
@ToString
public class Chat {

    private String aiModel;
    private String prompt;
}
