package com.koala.web.controller;

import com.koala.base.enums.NeteaseRequestQualityEnums;
import com.koala.base.enums.NeteaseRequestSearchTypeEnums;
import com.koala.base.enums.NeteaseRequestTypeEnums;
import com.koala.data.models.netease.MultiMvQualityInfoModel;
import com.koala.data.models.netease.NeteaseMusicDataRespModel;
import com.koala.data.models.netease.mvInfo.NeteaseMusicMvInfoRespModel;
import com.koala.data.models.shortUrl.ShortNeteaseItemDataModel;
import com.koala.data.models.shortUrl.ShortNeteaseMvItemDataModel;
import com.koala.factory.builder.ConcreteNeteaseApiBuilder;
import com.koala.factory.builder.NeteaseApiBuilder;
import com.koala.factory.director.NeteaseApiManager;
import com.koala.factory.extra.NeteaseCookieUtil;
import com.koala.factory.path.NeteaseWebPathCollector;
import com.koala.factory.product.NeteaseApiProduct;
import com.koala.service.custom.http.annotation.HttpRequestRecorder;
import com.koala.service.data.redis.service.RedisService;
import com.koala.service.utils.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.koala.base.enums.NeteaseResponseEnums.*;
import static com.koala.service.data.redis.RedisKeyPrefix.*;
import static com.koala.service.utils.RespUtil.formatRespData;

/**
 * @author koala
 * @version 1.0
 * @date 2023/6/16 14:13
 * @description
 */
@RestController
@RequestMapping("tools/Netease")
public class NeteaseToolsController {

    private static final Logger logger = LoggerFactory.getLogger(NeteaseToolsController.class);

    private static final Long EXPIRE_TIME = 12 * 60 * 60L;

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Resource(name = "getHost")
    private String host;

    @Resource(name = "RedisService")
    private RedisService redisService;

    @Resource
    private NeteaseCookieUtil neteaseCookieUtil;

    @HttpRequestRecorder
    @GetMapping(value = "api", produces = {"application/json;charset=utf-8"})
    public Object getNeteaseMusic(@RequestParam(required = false) String link, @RequestParam(required = false) String id, @RequestParam(required = false, name = "type", defaultValue = "info") String type, @RequestParam(value = "quality", required = false, defaultValue = "") String quality, @RequestParam(required = false, defaultValue = "false") String lyric, @RequestParam(required = false, name = "version", defaultValue = "1") String version, HttpServletRequest request, HttpServletResponse response) {
        if (!StringUtils.hasLength(link) && !StringUtils.hasLength(id)) {
            return formatRespData(INVALID_LINK, null);
        }
        String url;
        if (StringUtils.hasLength(id)) {
            url = "https://music.163.com/#/song?id=" + id;
        } else {
            Optional<String> optional = Arrays.stream(link.split(" ")).filter(item -> item.contains("music.163.com/")).findFirst();
            if (optional.isPresent()) {
                url = optional.get().trim();
            } else {
                return formatRespData(INVALID_LINK, null);
            }
        }
        NeteaseApiBuilder builder = new ConcreteNeteaseApiBuilder();
        NeteaseApiManager manager = new NeteaseApiManager(builder);
        NeteaseApiProduct product = null;
        try {
            NeteaseRequestQualityEnums qualityEnums = NeteaseRequestQualityEnums.getEnumsByType(quality);
            if (!Objects.isNull(qualityEnums)) {
                product = manager.construct(redisService, host, neteaseCookieUtil.getNeteaseCookie(), url, qualityEnums.getType(), "true".equals(lyric), Integer.valueOf(version));
            } else {
                product = manager.construct(redisService, host, neteaseCookieUtil.getNeteaseCookie(), url, null, "true".equals(lyric), Integer.valueOf(version));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return formatRespData(FAILURE, null);
        }
        NeteaseMusicDataRespModel publicData = product.generateItemInfoRespData();
        try {
            switch (Objects.requireNonNull(NeteaseRequestTypeEnums.getEnumsByType(type))) {
                case INFO -> {
                    return formatRespData(GET_DATA_SUCCESS, publicData);
                }
                case PREVIEW_MUSIC -> {
                    if (!publicData.getItemInfo().getData().isEmpty() && StringUtils.hasLength(publicData.getItemInfo().getData().get(0).getMockPreviewPath())) {
                        redirectStrategy.sendRedirect(request, response, publicData.getItemInfo().getData().get(0).getMockPreviewPath());
                    }
                }
                case DOWNLOAD -> {
                    if (!publicData.getItemInfo().getData().isEmpty() && StringUtils.hasLength(publicData.getItemInfo().getData().get(0).getMockDownloadPath())) {
                        redirectStrategy.sendRedirect(request, response, publicData.getItemInfo().getData().get(0).getMockDownloadPath());
                    }
                }
                default -> {
                    return formatRespData(UNSUPPORTED_TYPE, null);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return formatRespData(GET_INFO_ERROR, null);
    }

    @HttpRequestRecorder
    @GetMapping(value = "api/lyric", produces = {"application/json;charset=utf-8"})
    public Object getNeteaseMusic(@RequestParam(required = false) String link, @RequestParam(required = false, name = "version", defaultValue = "1") String version, HttpServletRequest request, HttpServletResponse response) {
        if (!StringUtils.hasLength(link)) {
            return formatRespData(INVALID_LINK, null);
        }
        String url;
        Optional<String> optional = Arrays.stream(link.split(" ")).filter(item -> item.contains("music.163.com/")).findFirst();
        if (optional.isPresent()) {
            url = optional.get().trim();
        } else {
            return formatRespData(INVALID_LINK, null);
        }
        NeteaseApiBuilder builder = new ConcreteNeteaseApiBuilder();
        NeteaseApiManager manager = new NeteaseApiManager(builder);
        NeteaseApiProduct product = null;
        try {
            product = manager.construct(redisService, host, neteaseCookieUtil.getNeteaseCookie(), url, Integer.valueOf(version));
            NeteaseMusicDataRespModel publicData = product.generateItemInfoRespData();
            if (!Objects.isNull(publicData)) {
                return formatRespData(GET_DATA_SUCCESS, publicData);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return formatRespData(FAILURE, null);
        }
        return formatRespData(GET_INFO_ERROR, null);
    }

    @HttpRequestRecorder
    @GetMapping("download/music/short")
    public void downloadMusic(@RequestParam(required = false) String key, HttpServletRequest request, HttpServletResponse response) {
        try {
            String itemKey = "".equals(key) ? "" : new String(Base64Utils.decodeFromUrlSafeString(key));
            logger.info("[musicPlayer] itemKey: {}, Sec-Fetch-Dest: {}", itemKey, request.getHeader("Sec-Fetch-Dest"));
            if (StringUtils.hasLength(itemKey)) {
                ShortNeteaseItemDataModel tmp = GsonUtil.toBean(redisService.get(NETEASE_DATA_KEY_PREFIX + itemKey), ShortNeteaseItemDataModel.class);
                String artist = StringUtils.hasLength(tmp.getArtist()) ? " - " + tmp.getArtist() : "";
                String fileName = StringUtils.hasLength(tmp.getTitle()) ? tmp.getTitle() + artist : UUID.randomUUID().toString().replace("-", "");
                HeaderUtil.getMockDownloadMusicHeader(fileName, tmp.getType()).forEach(response::addHeader);
                redirectStrategy.sendRedirect(request, response, tmp.getOrigin());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @HttpRequestRecorder
    @GetMapping(value = "api/search", produces = "application/json;charset=UTF-8")
    public String search(@RequestParam(required = false) String text, @RequestParam(required = false, defaultValue = "1") String type, @RequestParam(required = false, defaultValue = "1") Long page, @RequestParam(required = false, defaultValue = "20") Integer limit) {
        if (Objects.isNull(NeteaseRequestSearchTypeEnums.getEnumsByType(type))) {
            return formatRespData(UNSUPPORTED_TYPE, null);
        }
        if (!StringUtils.hasLength(text) || page < 1 || limit < 0) {
            return formatRespData(UNSUPPORTED_PARAMS, null);
        }
        Map<String, String> params = new HashMap<>();
        params.put("s", text);
        params.put("type", type);
        params.put("offset", String.valueOf((page - 1) * limit));
        params.put("total", "true");
        params.put("limit", String.valueOf(limit));
        String response = null;
        try {
            response = HttpClientUtil.doPost(NeteaseWebPathCollector.NETEASE_SEARCH_WEB_SERVER_URL_V1, HeaderUtil.getNeteasePublicWithOutCookieHeader(), params);
        } catch (Exception e) {
            return formatRespData(FAILURE, null);
        }
        if (StringUtils.hasLength(response)) {
            HashMap<String, Object> result = new HashMap<>();
            result.put("page", page);
            result.put("limit", limit);
            result.put("response", GsonUtil.toBean(response, Object.class));
            return formatRespData(GET_DATA_SUCCESS, result);
        }
        return formatRespData(GET_INFO_ERROR, null);
    }

    @HttpRequestRecorder
    @GetMapping(value = "api/mv", produces = "application/json;charset=UTF-8")
    public String mv(@RequestParam(required = false) String mid, @RequestParam(required = false, name = "type", defaultValue = "info") String type, HttpServletRequest request, HttpServletResponse response) {
        if (!StringUtils.hasLength(mid)) {
            return formatRespData(UNSUPPORTED_PARAMS, null);
        }
        Map<String, String> params = new HashMap<>();
        params.put("id", mid);
        params.put("type", "mp4");
        String resp = null;
        try {
            resp = HttpClientUtil.doPost(NeteaseWebPathCollector.NETEASE_MV_DETAIL_SERVER_URL, HeaderUtil.getNeteasePublicWithOutCookieHeader(), params);
        } catch (Exception e) {
            return formatRespData(FAILURE, null);
        }
        if (StringUtils.hasLength(resp)) {
            try {
                NeteaseMusicMvInfoRespModel mvInfo = GsonUtil.toBean(resp, NeteaseMusicMvInfoRespModel.class);
                String key = ShortKeyGenerator.getKey(null);
                String title = mvInfo.getData().getName() + Optional.of(" - " + mvInfo.getData().getArtistName()).orElse("");
                MultiMvQualityInfoModel multiQualityInfo = new MultiMvQualityInfoModel(
                        ShortKeyGenerator.generateShortUrl(mvInfo.getData().getBrs().getBrs1080(), EXPIRE_TIME, host, redisService).getUrl(),
                        ShortKeyGenerator.generateShortUrl(mvInfo.getData().getBrs().getBrs720(), EXPIRE_TIME, host, redisService).getUrl(),
                        ShortKeyGenerator.generateShortUrl(mvInfo.getData().getBrs().getBrs480(), EXPIRE_TIME, host, redisService).getUrl(),
                        ShortKeyGenerator.generateShortUrl(mvInfo.getData().getBrs().getBrs240(), EXPIRE_TIME, host, redisService).getUrl()
                );
                redisService.set(NETEASE_MV_DATA_KEY_PREFIX + key, GsonUtil.toString(new ShortNeteaseMvItemDataModel(title, null, multiQualityInfo)), EXPIRE_TIME);
                mvInfo.setMockPreviewPath(host + "tools/Netease/pro/player/mv/short?key=" + Base64Utils.encodeToUrlSafeString(key.getBytes(StandardCharsets.UTF_8)));
                switch (Objects.requireNonNull(NeteaseRequestTypeEnums.getEnumsByType(type))) {
                    case INFO -> {
                        return formatRespData(GET_DATA_SUCCESS, mvInfo);
                    }
                    case PREVIEW_MV -> {
                        if (StringUtils.hasLength(mvInfo.getMockPreviewPath())) {
                            redirectStrategy.sendRedirect(request, response, mvInfo.getMockPreviewPath());
                        }
                    }
                    default -> {
                        return formatRespData(UNSUPPORTED_TYPE, null);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return formatRespData(GET_INFO_ERROR, null);
    }

}
