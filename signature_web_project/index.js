const axios = require('axios');
const crypto = require('crypto');
const express = require("express");
const {sign} = require("./Signer.js");
const cryptoJs = require('crypto-js');

const app = express();
app.use(express.json());

app.post("/kugou/v1", async (req, res) => {
    try {
        const {params} = req.body;

        if (!params) {
            throw new Error("Missing required parameters.");
        }

        console.info({
            params: params,
        });

        res.status(200).json({
            code: 200,
            msg: "success",
            data: {
                signature: cryptoJs.MD5(params).toString(),
                params: params
            }
        });
    } catch (err) {
        console.error(err);
        res.status(400).json({code: 201, msg: err.message});
    }
});

app.post("/kugou/v2", async (req, res) => {
    try {
        const {params} = req.body;

        if (!params) {
            throw new Error("Missing required parameters.");
        }

        console.info({
            params: params,
        });

        res.status(200).json({
            code: 200,
            msg: "success",
            data: {
                key:cryptoJs.MD5(params).toString(),
                params: params
            }
        });
    } catch (err) {
        console.error(err);
        res.status(400).json({code: 201, msg: err.message});
    }
});

app.post("/tiktok", async (req, res) => {
    try {
        const {url, userAgent} = req.body;

        if (!url || !userAgent) {
            throw new Error("Missing required parameters.");
        }

        const query = url.includes("?") ? url.split("?")[1] : "";
        const xbogus = sign(query, userAgent);
        const newUrl = `${url}&X-Bogus=${xbogus}`;
        const [xbogusToken, ttwid] = await Promise.all([msToken(107), getTtwid()]);

        console.info({
            xbogus: xbogus,
            mstoken: xbogusToken,
            ttwid: ttwid,
            url: newUrl
        });

        res.status(200).json({
            code: 200,
            msg: "success",
            data: {
                xbogus: xbogus,
                mstoken: xbogusToken,
                ttwid: ttwid,
                url: newUrl
            }
        });
    } catch (err) {
        console.error(err);
        res.status(400).json({code: 201, msg: err.message});
    }
});

app.get("/", (req, res) => {
    res.status(200).send(`
    <html lang="en">
      <head>
        <title>Tiktok_Signature</title>
        <style>
          body {
            background-color: #f2f2f2;
            font-family: Arial, sans-serif;
            text-align: center;
          }
          h1 {
            color: #333;
            font-size: 3rem;
            margin-top: 3rem;
          }
          p {
            color: #666;
            font-size: 1.5rem;
            margin-top: 1.5rem;
          }
        </style>
      </head>
      <body>
        <h1>Signature Web</h1>
        <p>自动生成抖音 xbogus、mstoken 和 ttwid</p>
        <p>自动生成酷狗signature</p>
        <p>使用方法：<a href="https://github.com/5ime/Tiktok_Signature" target="_blank">https://github.com/5ime/Tiktok_Signature</a></p>
      </body>
    </html>
  `);
});

function msToken(length) {
    const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    const randomBytes = crypto.randomBytes(length);
    return Array.from(randomBytes, byte => characters[byte % characters.length]).join('');
}

async function getTtwid() {
    try {
        const url = 'https://ttwid.bytedance.com/ttwid/union/register/';
        const data = {
            "region": "cn",
            "aid": 1768,
            "needFid": false,
            "service": "www.ixigua.com",
            "migrate_info": {"ticket": "", "source": "node"},
            "cbUrlProtocol": "https",
            "union": true
        };
        const response = await axios.post(url, data, {headers: {'Content-Type': 'application/json'}});
        const setCookie = response.headers['set-cookie'];
        const regex = /ttwid=([^;]+)/;
        const match = regex.exec(setCookie[0]);
        return match && match.length > 1 ? match[1] : '';
    } catch (error) {
        console.error(error);
        return '';
    }
}

app.listen(8080, () => {
    console.log("Server is running on port 8080");
});