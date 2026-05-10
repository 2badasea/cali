package com.bada.cali.dto;

import com.bada.cali.common.enums.YnType;
import com.bada.cali.entity.Menu;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class MenuDTO {

    // ── 트리 조회 응답 ──────────────────────────────────────────────────────────

    @Getter
    @Schema(description = "메뉴 트리 노드")
    public static class TreeRes {

        @Schema(description = "메뉴 ID")
        private final Long id;

        @Schema(description = "메뉴명(화면 표시)")
        private final String menuAlias;

        @Schema(description = "메뉴 코드(고유)")
        private final String menuCode;

        @Schema(description = "링크 URL")
        private final String url;

        @Schema(description = "링크 방식 (_self / _blank)")
        private final String target;

        @Schema(description = "메뉴 깊이 (1=대메뉴, 2=하위메뉴)")
        private final Integer depth;

        @Schema(description = "동일 depth 내 정렬 순서")
        private final Integer sortOrder;

        @Schema(description = "노출 여부")
        private final YnType isVisible;

        @Schema(description = "상위 메뉴 ID (대메뉴는 null)")
        private final Long parentId;

        @Schema(description = "하위 메뉴 목록")
        private final List<TreeRes> children = new ArrayList<>();

        private TreeRes(Long id, String menuAlias, String menuCode, String url,
                        String target, Integer depth, Integer sortOrder,
                        YnType isVisible, Long parentId) {
            this.id = id;
            this.menuAlias = menuAlias;
            this.menuCode = menuCode;
            this.url = url;
            this.target = target;
            this.depth = depth;
            this.sortOrder = sortOrder;
            this.isVisible = isVisible;
            this.parentId = parentId;
        }

        public static TreeRes from(Menu menu) {
            return new TreeRes(
                    menu.getId(),
                    menu.getMenuAlias(),
                    menu.getMenuCode(),
                    menu.getUrl(),
                    menu.getTarget(),
                    menu.getDepth(),
                    menu.getSortOrder(),
                    menu.getIsVisible(),
                    menu.getParent() != null ? menu.getParent().getId() : null
            );
        }
    }

    // ── 메뉴 등록 요청 ─────────────────────────────────────────────────────────

    @Getter
    @Schema(description = "메뉴 등록 요청")
    public static class CreateReq {

        @NotBlank
        @Size(max = 100)
        @Schema(description = "메뉴명", requiredMode = Schema.RequiredMode.REQUIRED)
        private String menuAlias;

        @NotBlank
        @Size(max = 50)
        @Schema(description = "메뉴 코드 (영문·언더스코어, 유니크)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String menuCode;

        @Size(max = 255)
        @Schema(description = "링크 URL")
        private String url;

        @Schema(description = "링크 방식 (_self / _blank)", defaultValue = "_self")
        private String target = "_self";

        @NotNull
        @Schema(description = "노출 여부", defaultValue = "y")
        private YnType isVisible = YnType.y;

        @Schema(description = "상위 메뉴 ID (null이면 대메뉴로 생성)")
        private Long parentId;
    }

    // ── 메뉴 수정 요청 ─────────────────────────────────────────────────────────

    @Getter
    @Schema(description = "메뉴 수정 요청")
    public static class UpdateReq {

        @NotBlank
        @Size(max = 100)
        @Schema(description = "메뉴명", requiredMode = Schema.RequiredMode.REQUIRED)
        private String menuAlias;

        @NotBlank
        @Size(max = 50)
        @Schema(description = "메뉴 코드 (영문·언더스코어, 유니크)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String menuCode;

        @Size(max = 255)
        @Schema(description = "링크 URL")
        private String url;

        @Schema(description = "링크 방식 (_self / _blank)")
        private String target;

        @NotNull
        @Schema(description = "노출 여부")
        private YnType isVisible;
    }

    // ── 순서 변경 요청 (형제 메뉴 일괄 업데이트용) ─────────────────────────────

    @Getter
    @Schema(description = "메뉴 순서 변경 항목")
    public static class ReorderItem {

        @NotNull
        @Schema(description = "메뉴 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long id;

        @NotNull
        @Schema(description = "변경할 sort_order 값", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer sortOrder;
    }
}
