package com.zvonok.service.dto;

import com.zvonok.model.Server;
import lombok.Data;

@Data
public class CreateServerRoleDto {

    private String name;
    private String color;
    private Integer position;
    private Long serverPermissions;
    private boolean isEveryone;
    private boolean mentionable;
    private Server server;
}
