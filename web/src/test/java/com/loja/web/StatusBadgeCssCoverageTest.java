package com.loja.web;

import com.loja.ordercheckout.domain.model.OrderStatus;
import com.loja.ordercheckout.domain.model.RefundStatus;
import com.loja.productcatalog.domain.model.ProductStatus;
import com.loja.useraccount.domain.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards {@code status-badge.xhtml}'s CSS contract against drift between the domain
 * ({@link OrderStatus}, {@link ProductStatus}, {@link UserStatus}) and the two style
 * layers that render it ({@code base.css}, {@code design-tokens.css}).
 *
 * <p>Written after a real bug: {@code OrderStatus} grew from 3 values (OPEN, CONFIRMED,
 * CANCELLED) to 7 (PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED,
 * REFUNDED) and the CSS was never updated to match -- 4 of 7 order statuses rendered
 * with no color, and a dead rule for the removed OPEN value lingered indefinitely. See
 * docs/lessons.md and docs/design-system.md section 0 ("design-code drift").
 *
 * <p>Checks three hops, each direction:
 * <ol>
 * <li>Every enum constant across all four Status enums has a matching
 *       {@code .status-badge.status-<NAME>} rule in {@code base.css}, and vice versa
 *       (no rule survives after its enum value is removed).</li>
 *   <li>Every {@code --color-status-*} token referenced by those CSS rules is actually
 *       declared in {@code design-tokens.css}, and vice versa (no orphan token nobody
 *       renders).</li>
 * </ol>
 *
 * <p>If this test fails after adding a new Status enum value, add the missing
 * {@code --color-status-<name>} token in {@code design-tokens.css} and the matching
 * {@code .status-badge.status-<NAME>} rule in {@code base.css} -- see design-system.md
 * section 4 (governance) before choosing a color: semantic status tokens are Strict
 * tier, get human sign-off on the primitive/hex choice.
 */
class StatusBadgeCssCoverageTest {

    private static final Path BASE_CSS = Path.of("src/main/webapp/resources/css/base.css");
    private static final Path DESIGN_TOKENS_CSS = Path.of("src/main/webapp/resources/css/design-tokens.css");

    private static final Pattern CSS_RULE_PATTERN =
            Pattern.compile("\\.status-badge\\.status-([A-Z_]+)\\s*\\{\\s*color:\\s*var\\((--color-status-[a-z-]+)\\)");
    private static final Pattern TOKEN_DECLARATION_PATTERN =
            Pattern.compile("(--color-status-[a-z-]+)\\s*:");

    @Test
    void everyStatusEnumConstantHasAMatchingCssRule() {
        Map<String, Set<String>> expectedByEnum = Map.of(
                "OrderStatus", enumNames(OrderStatus.values()),
                "ProductStatus", enumNames(ProductStatus.values()),
                "UserStatus", enumNames(UserStatus.values()),
                "RefundStatus", enumNames(RefundStatus.values())
        );
        Set<String> allExpected = expectedByEnum.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        Set<String> cssClasses = parseCssRules(readFile(BASE_CSS)).keySet();

        expectedByEnum.forEach((enumName, constants) -> {
            Set<String> missing = new HashSet<>(constants);
            missing.removeAll(cssClasses);
            assertThat(missing)
                    .as("%s constant(s) with no '.status-badge.status-<NAME>' rule in %s. "
                                    + "A badge for this status will render with no color. "
                                    + "Add the rule (and its --color-status-* token) before merging.",
                            enumName, BASE_CSS)
                    .isEmpty();
        });

        Set<String> orphanCssClasses = new HashSet<>(cssClasses);
        orphanCssClasses.removeAll(allExpected);
        assertThat(orphanCssClasses)
                .as("'.status-badge.status-<NAME>' rule(s) in %s with no matching constant in "
                                + "OrderStatus, ProductStatus, UserStatus, or RefundStatus. This is dead CSS left over "
                                + "from a renamed/removed enum value -- delete the rule.",
                        BASE_CSS)
                .isEmpty();
    }

    @Test
    void everyCssStatusRuleReferencesATokenThatActuallyExists() {
        Map<String, String> cssRules = parseCssRules(readFile(BASE_CSS));
        Set<String> referencedTokens = new HashSet<>(cssRules.values());
        Set<String> declaredTokens = parseTokenDeclarations(readFile(DESIGN_TOKENS_CSS));

        Set<String> referencedButNotDeclared = new HashSet<>(referencedTokens);
        referencedButNotDeclared.removeAll(declaredTokens);
        assertThat(referencedButNotDeclared)
                .as("%s references --color-status-* token(s) that are not declared in %s. "
                                + "The badge will render with no color (undefined CSS variable).",
                        BASE_CSS, DESIGN_TOKENS_CSS)
                .isEmpty();

        Set<String> declaredButUnused = new HashSet<>(declaredTokens);
        declaredButUnused.removeAll(referencedTokens);
        assertThat(declaredButUnused)
                .as("%s declares --color-status-* token(s) that no rule in %s references. "
                                + "Dead token left over from a renamed/removed status -- delete it.",
                        DESIGN_TOKENS_CSS, BASE_CSS)
                .isEmpty();
    }

    private static Set<String> enumNames(Enum<?>[] values) {
        return Stream.of(values).map(Enum::name).collect(Collectors.toSet());
    }

    /** @return map of CSS class suffix (e.g. "SHIPPED") -> the --color-status-* token it uses. */
    private static Map<String, String> parseCssRules(String css) {
        Map<String, String> rules = new HashMap<>();
        Matcher matcher = CSS_RULE_PATTERN.matcher(css);
        while (matcher.find()) {
            rules.put(matcher.group(1), matcher.group(2));
        }
        return rules;
    }

    private static Set<String> parseTokenDeclarations(String css) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = TOKEN_DECLARATION_PATTERN.matcher(css);
        while (matcher.find()) {
            tokens.add(matcher.group(1));
        }
        return tokens;
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not read " + path.toAbsolutePath()
                            + " -- this test must run with the 'web' module as the working "
                            + "directory (Maven Surefire's default; run via 'mvn test' from web/ "
                            + "or the reactor, not from an IDE run configuration with a different cwd).",
                    e);
        }
    }
}
