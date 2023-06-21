package com.koala.web.controller;

import com.koala.base.enums.KugouRequestQualityEnums;
import com.koala.base.enums.KugouRequestTypeEnums;
import com.koala.data.models.kugou.KugouMusicDataRespModel;
import com.koala.data.models.kugou.config.KugouProductConfigModel;
import com.koala.data.models.kugou.playInfo.KugouPlayInfoRespDataModel;
import com.koala.data.models.shortUrl.ShortKugouItemDataModel;
import com.koala.factory.builder.ConcreteKugouApiBuilder;
import com.koala.factory.builder.KugouApiBuilder;
import com.koala.factory.director.KugouApiManager;
import com.koala.factory.extra.kugou.KugouCustomParamsUtil;
import com.koala.factory.extra.kugou.KugouMidGenerator;
import com.koala.factory.extra.kugou.KugouPlayInfoParamsGenerator;
import com.koala.factory.product.KugouApiProduct;
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static com.koala.base.enums.KugouResponseEnums.*;
import static com.koala.factory.extra.kugou.KugouSearchParamsGenerator.getSearchParams;
import static com.koala.factory.extra.kugou.KugouSearchParamsGenerator.getSearchTextParams;
import static com.koala.factory.path.KugouWebPathCollector.*;
import static com.koala.service.data.redis.RedisKeyPrefix.KUGOU_DATA_KEY_PREFIX;
import static com.koala.service.utils.RespUtil.formatRespData;

/**
 * @author koala
 * @version 1.0
 * @date 2023/6/19 20:03
 * @description
 */
@RestController
@RequestMapping("tools/Kugou")
public class KugouToolsController {

    private static final Logger logger = LoggerFactory.getLogger(KugouToolsController.class);

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Resource(name = "getHost")
    private String host;

    @Resource(name = "RedisService")
    private RedisService redisService;

    @Resource
    private KugouCustomParamsUtil customParams;

    @HttpRequestRecorder
    @GetMapping(value = "api/search", produces = {"application/json;charset=utf-8"})
    public String search(@RequestParam(required = false) String key, @RequestParam(required = false, defaultValue = "1") Long page, @RequestParam(required = false, defaultValue = "30") Long limit) throws IOException, URISyntaxException {
        Long timestamp = System.currentTimeMillis();
        String mid = KugouMidGenerator.getMid();
        String signature = MD5Utils.md5(getSearchTextParams(timestamp, key, mid, page, limit, customParams));
        if (!StringUtils.hasLength(signature)) {
            return formatRespData(GET_SIGNATURE_FAILED, null);
        }
        String response = HttpClientUtil.doGet(KUGOU_SEARCH_WEB_SERVER_URL_V1, HeaderUtil.getKugouPublicHeader(null, null), getSearchParams(timestamp, key, mid, page, limit, signature, customParams));
        if (StringUtils.hasLength(response)) {
            return formatRespData(GET_DATA_SUCCESS, GsonUtil.toBean(response, Object.class));
        }
        return formatRespData(GET_INFO_ERROR, null);
    }

    @HttpRequestRecorder
    @GetMapping(value = "api/search/tips", produces = {"application/json;charset=utf-8"})
    public String searchTip(@RequestParam(required = false) String text) throws IOException, URISyntaxException {
        HashMap<String, String> params = new HashMap<>();
        params.put("keyword", text);
        String response = HttpClientUtil.doGet(KUGOU_SEARCH_TIP_SERVER_URL, HeaderUtil.getKugouPublicHeader(null, null), params);
        if (StringUtils.hasLength(response)) {
            return formatRespData(GET_DATA_SUCCESS, GsonUtil.toBean(response, Object.class));
        }
        return formatRespData(GET_INFO_ERROR, null);
    }

    @HttpRequestRecorder
    @GetMapping(value = "api/search/mv", produces = {"application/json;charset=utf-8"})
    public String searchMv(@RequestParam(required = false) String text, @RequestParam(required = false, defaultValue = "1") Long page, @RequestParam(required = false, defaultValue = "30") Long limit) throws IOException, URISyntaxException {
        HashMap<String, String> params = new HashMap<>();
        params.put("keyword", text);
        params.put("pagesize", limit.toString());
        params.put("page", page.toString());
        String response = HttpClientUtil.doGet(KUGOU_SEARCH_MV_SERVER_URL, HeaderUtil.getKugouPublicHeader(null, null), params);
        if (StringUtils.hasLength(response)) {
            return formatRespData(GET_DATA_SUCCESS, GsonUtil.toBean(response, Object.class));
        }
        return formatRespData(GET_INFO_ERROR, null);
    }

    @HttpRequestRecorder
    @GetMapping(value = "api", produces = {"application/json;charset=utf-8"})
    public String api(@RequestParam(required = false) String link, @RequestParam(required = false) String hash, @RequestParam(required = false) String albumId, @RequestParam(required = false, name = "type", defaultValue = "info") String type, @RequestParam(required = false, defaultValue = "1") Integer version, @RequestParam(required = false, defaultValue = "false") String albumInfo, @RequestParam(required = false, defaultValue = "false") String albumMusicInfo, @RequestParam(required = false, defaultValue = "false") String musicInfo, HttpServletRequest request, HttpServletResponse response) throws IOException, URISyntaxException {
        if (!StringUtils.hasLength(link) && (!StringUtils.hasLength(hash) && !StringUtils.hasLength(albumId))) {
            return formatRespData(UNSUPPORTED_PARAMS, null);
        }
        String url = null;
        if (StringUtils.hasLength(link)) {
            Optional<String> optional = Arrays.stream(link.replaceFirst("http://", " http://").replaceFirst("https://", " https://").split(" ")).filter(item -> item.contains("kugou.com/")).findFirst();
            if (optional.isPresent()) {
                url = optional.get().trim();
            } else {
                return formatRespData(INVALID_LINK, null);
            }
        }
        KugouProductConfigModel config = new KugouProductConfigModel(
                "true".equals(albumInfo),
                "true".equals(albumMusicInfo),
                "true".equals(musicInfo)
        );
        KugouApiBuilder builder = new ConcreteKugouApiBuilder();
        KugouApiManager manager = new KugouApiManager(builder);
        KugouApiProduct product = null;
        try {
            product = manager.construct(redisService, host, url, hash, albumId, version, customParams, config);
        } catch (Exception e) {
            e.printStackTrace();
            return formatRespData(FAILURE, null);
        }
        KugouMusicDataRespModel publicData = product.generateItemInfoRespData();
        try {
            switch (Objects.requireNonNull(KugouRequestTypeEnums.getEnumsByType(type))) {
                case INFO -> {
                    return formatRespData(GET_DATA_SUCCESS, publicData);
                }
                case PREVIEW_MUSIC -> {
                    String defaultPath = publicData.getMockPreviewPath().get(KugouRequestQualityEnums.QUALITY_DEFAULT.getType());
                    if (StringUtils.hasLength(defaultPath)) {
                        redirectStrategy.sendRedirect(request, response, defaultPath);
                    }
                }
                case DOWNLOAD -> {
                    String defaultPath = publicData.getMockDownloadPath().get(KugouRequestQualityEnums.QUALITY_DEFAULT.getType());
                    if (StringUtils.hasLength(defaultPath)) {
                        redirectStrategy.sendRedirect(request, response, defaultPath);
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
    @GetMapping(value = "api/playInfo", produces = {"application/json;charset=utf-8"})
    public String playInfo(@RequestParam(required = false) String hash, @RequestParam(required = false) String albumId) throws IOException, URISyntaxException {
        String mid = KugouMidGenerator.getMid();
        String cookie = customParams.getKugouCustomParams().get("kg_mid_cookie").toString();
        String response = HttpClientUtil.doGet(KUGOU_DETAIL_SERVER_URL_V2, HeaderUtil.getKugouPublicHeader(null, cookie), KugouPlayInfoParamsGenerator.getPlayInfoParams(hash, mid, albumId, customParams));
        if (StringUtils.hasLength(response)) {
            return formatRespData(GET_DATA_SUCCESS, GsonUtil.toBean(response, Object.class));
        }
        return formatRespData(GET_INFO_ERROR, null);
    }

    @HttpRequestRecorder
    @GetMapping(value = "api/mv/detail", produces = {"application/json;charset=utf-8"})
    public String mvDetail(@RequestParam(required = false) String hash) throws IOException, URISyntaxException {
        if (!StringUtils.hasLength(hash)) {
            return formatRespData(UNSUPPORTED_PARAMS, null);
        }
        String mid = KugouMidGenerator.getMid();
        String cookie = customParams.getKugouCustomParams().get("kg_mid_cookie").toString();
        Long timestamp = System.currentTimeMillis();
        HashMap<String, String> params = new HashMap<>();
        params.put("clienttime", String.valueOf(timestamp));
        params.put("mid", mid);
        params.put("clientver", "312");
        params.put("data", "[{\"video_hash\":\"" + hash + "\"}]");
        params.put("key", "");
        params.put("appid", "1155");
        String response = HttpClientUtil.doGet(KUGOU_MV_DETAIL_SERVER_URL, HeaderUtil.getKugouPublicHeader(null, cookie), params);
        if (StringUtils.hasLength(response)) {
            return formatRespData(GET_DATA_SUCCESS, GsonUtil.toBean(response, Object.class));
        }
        return formatRespData(GET_INFO_ERROR, null);
    }

    @HttpRequestRecorder
    @GetMapping(value = "download/music/short", produces = "application/json;charset=UTF-8")
    public void downloadMusic(@RequestParam(required = false) String key, @RequestParam(value = "quality", required = false, defaultValue = "default") String quality, HttpServletRequest request, HttpServletResponse response) {
        try {
            String itemKey = "".equals(key) ? "" : new String(Base64Utils.decodeFromUrlSafeString(key));
            logger.info("[musicPlayer] itemKey: {}, Sec-Fetch-Dest: {}", itemKey, request.getHeader("Sec-Fetch-Dest"));
            if (Objects.isNull(KugouRequestQualityEnums.getEnumsByType(quality))) {
                return;
            }
            if (StringUtils.hasLength(itemKey)) {
                ShortKugouItemDataModel tmp = GsonUtil.toBean(redisService.get(KUGOU_DATA_KEY_PREFIX + itemKey), ShortKugouItemDataModel.class);
                String artist = StringUtils.hasLength(tmp.getAuthorName()) ? " - " + tmp.getAuthorName() : "";
                String fileName = StringUtils.hasLength(tmp.getTitle()) ? tmp.getTitle() + artist : UUID.randomUUID().toString().replace("-", "");
                String hash = tmp.getMusicInfo().getAudioInfo().getPlayInfoList().get(quality).getHash();
                String albumId = tmp.getMusicInfo().getAlbumInfo().getAlbumId();
                String mid = KugouMidGenerator.getMid();
                String cookie = customParams.getKugouCustomParams().get("kg_mid_cookie").toString();
                String resp = HttpClientUtil.doGet(KUGOU_DETAIL_SERVER_URL_V2, HeaderUtil.getKugouPublicHeader(null, cookie), KugouPlayInfoParamsGenerator.getPlayInfoParams(hash, mid, albumId, customParams));
                KugouPlayInfoRespDataModel respData = null;
                if (StringUtils.hasLength(resp)) {
                    respData = GsonUtil.toBean(resp, KugouPlayInfoRespDataModel.class);
                }
                if (Objects.isNull(respData) || respData.getUrl().isEmpty()) {
                    return;
                }
                HttpClientUtil.doRelay(respData.getUrl().get(0), HeaderUtil.getKugouAudioDownloadHeader(), null, 206, HeaderUtil.getMockDownloadKugouFileHeader(fileName, respData.getExtName()), request, response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
