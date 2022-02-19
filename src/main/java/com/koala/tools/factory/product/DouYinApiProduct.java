package com.koala.tools.factory.product;

import com.koala.tools.models.douyin.ItemInfoRespModel;
import com.koala.tools.utils.GsonUtil;
import com.koala.tools.utils.HeaderUtil;
import com.koala.tools.utils.HttpClientUtil;
import com.koala.tools.utils.PatternUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * @author koala
 * @version 1.0
 * @date 2022/2/19 12:34
 * @description
 */
public class DouYinApiProduct {
    private static final Logger logger = LoggerFactory.getLogger(DouYinApiProduct.class);
    private String url;
    private String directUrl;
    private String id;
    private String itemId;
    private ItemInfoRespModel itemInfo;

    public void setUrl(String url) {
        this.url = url;
    }

    public void getIdByUrl() {
        if (!Objects.isNull(this.url)) {
            this.id = PatternUtil.matchData("douyin.com/(.*?)/", this.url);
        }
    }

    public void getItemIdByDirectUrl() {
        if (!Objects.isNull(this.directUrl)) {
            this.itemId = PatternUtil.matchData("video/(.*?)/", this.directUrl);
        }
    }

    public void getRedirectUrl() throws IOException, URISyntaxException {
        if (!Objects.isNull(this.url)) {
            this.directUrl = HttpClientUtil.doGetRedirectLocation(this.url, null, HeaderUtil.getDouYinDownloadHeader());
        }
    }

    public void getItemInfoData() throws IOException, URISyntaxException {
        if (!Objects.isNull(itemId)) {
            String itemInfoPath = "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=" + itemId;
            String itemInfoResponse = HttpClientUtil.doGet(itemInfoPath, null, HeaderUtil.getDouYinDownloadHeader());
            logger.info("[DouYinApiProduct]({}, {}) itemInfoResponse: {}}", id, itemId, itemInfoResponse);
            try {
                this.itemInfo = GsonUtil.toBean(itemInfoResponse, ItemInfoRespModel.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public ItemInfoRespModel getItemInfo() {
        return itemInfo;
    }

    /**
     * 下面是打印log区域
     */
    public void printParams() {
        logger.info("[DouYinApiProduct]({}, {}) params: {url={}, directPath={}}", id, itemId, url, directUrl);
    }

    public void generateData() {
        // TODO: 生成数据
    }
}
