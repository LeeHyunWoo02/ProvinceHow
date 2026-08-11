package SDD.smash.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

@Slf4j
@RequiredArgsConstructor
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final ApiRateLimitService apiRateLimitService;

    private final Environment env;

    private final String secret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException
    {
        // 프리플라이트의 경우 통과
        if("OPTIONS".equalsIgnoreCase(request.getMethod()))
        {
            filterChain.doFilter(request, response);
            return;
        }

        // 개발환경인 경우 레이트리밋을 적용하지 않음
        if(isDevProfileActive())
        {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = resolveIpThatConnectedToNginx(request);

        // 리버스 프록시를 통한 요청이 아니거나, ip를 확인할 수 없는 경우 요청 거절
        if(ip == null)
        {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return;
        }

        //ip 암호화
        ip = hmacSha256Hex(ip.trim());//암호화

        //암호화가 잘못되었다면, 저장하지 않고 통과
        if(ip == null)
        {
            filterChain.doFilter(request, response);
            return;
        }

        // 락 여부 확인
        long ipTtl = apiRateLimitService.checkIpLockAndINCAndMaybeLock(ip);
        if(ipTtl > 0)
        {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(ipTtl)); // 초 단위
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isDevProfileActive() {
        return Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("dev"));
    }


    /**
     * 운영환경은 반드시 Nginx를 리버스 프록시로 사용한다고 가정함.
     * XFF의 가장 오른쪽, Nginx로 직접 들어온 IP(Nginx가 XFF에 추가한 값)가 사용자의 클라이언트 IP
     */
    private String resolveIpThatConnectedToNginx(HttpServletRequest request)
    {
        String xff = request.getHeader("X-Forwarded-For");
        if(xff == null || xff.isBlank())
        {
            return request.getRemoteAddr();
        }
        String[] parts = xff.split(",");
        String rightMost = parts[parts.length - 1].trim();
        return rightMost.isEmpty() ? null : rightMost;
    }

    private String hmacSha256Hex(String input)
    {
        try{
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();

        } catch (NoSuchAlgorithmException e)
        {
            log.error("[RateLimitFilter] IP 암호화 알고리즘이 존재하지 않습니다.");
            return null;
        } catch (InvalidKeyException e)
        {
            log.error("[RateLimitFilter] IP 암호화 시크릿이 잘못되었습니다.");
            return null;
        } catch (Exception e)
        {
            log.error("[RateLimitFilter] IP 암호화 실패");
            return null;
        }
    }
}
