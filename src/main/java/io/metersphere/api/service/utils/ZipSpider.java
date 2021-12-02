package io.metersphere.api.service.utils;

import io.metersphere.node.util.LogUtil;
import org.apache.jmeter.config.CSVDataSet;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.protocol.http.util.HTTPFileArg;
import org.apache.jorphan.collections.HashTree;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipSpider {

    //根据网址返回网页源代码
    public static String getHtmlFromUrl(String url, String encoding) {
        StringBuffer html = new StringBuffer();
        InputStreamReader isr = null;
        BufferedReader buf = null;
        String str = null;
        try {
            URL urlObj = new URL(url);
            URLConnection con = urlObj.openConnection();
            isr = new InputStreamReader(con.getInputStream(), encoding);
            buf = new BufferedReader(isr);
            while ((str = buf.readLine()) != null) {
                html.append(str + "\n");
            }
            //sop(html.toString());
        } catch (Exception e) {
            LogUtil.error(e);
        } finally {
            if (isr != null) {
                try {
                    buf.close();
                    isr.close();
                } catch (IOException e) {
                    LogUtil.error(e);

                }
            }
        }
        return html.toString();
    }

    //根据网址下载网络文件到硬盘，包括图片，Gif图，以及压缩包
    public static void download(String url, String path) {
        File file = null;
        FileOutputStream fos = null;
        String downloadName = url.substring(url.lastIndexOf("/") + 1);
        HttpURLConnection httpCon = null;
        URLConnection con = null;
        URL urlObj = null;
        InputStream in = null;
        byte[] size = new byte[1024];
        int num = 0;
        try {
            file = new File(path + downloadName);
            fos = new FileOutputStream(file);
            if (url.startsWith("http")) {
                urlObj = new URL(url);
                con = urlObj.openConnection();
                httpCon = (HttpURLConnection) con;
                in = httpCon.getInputStream();
                while ((num = in.read(size)) != -1) {
                    for (int i = 0; i < num; i++)
                        fos.write(size[i]);
                }
            }

        } catch (Exception e) {
            LogUtil.error(e);
        } finally {
            try {
                in.close();
                fos.close();
            } catch (Exception e) {
                LogUtil.error(e);
            }
        }
    }

    //解压本地文件至目的文件路径
    public static void unzip(String fromFile, String toFile) {
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(fromFile)); BufferedInputStream bin = new BufferedInputStream(zin);) {
            String Parent = toFile;
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null && !entry.isDirectory()) {
                File fout = new File(Parent, entry.getName());
                if (!fout.exists()) {
                    (new File(fout.getParent())).mkdirs();
                }
                try (FileOutputStream out = new FileOutputStream(fout);
                     BufferedOutputStream bout = new BufferedOutputStream(out);) {
                    int b;
                    while ((b = bin.read()) != -1) {
                        bout.write(b);
                    }
                    LogUtil.info(fout + "解压成功");
                } catch (Exception e) {
                    LogUtil.error(e);
                }
            }
        } catch (FileNotFoundException e) {
            LogUtil.error(e);
        } catch (IOException e) {
            LogUtil.error(e);
        }
    }

    //从总目录下解压文件里所有的压缩包至目的文件路径
    public static void unzipFromLoc(String filePath) throws Exception {
        File file = new File(filePath);
        File[] list = file.listFiles();
        String from = "";
        String to = "E:\\myDownload\\unzipFileFromWeb\\";
        for (File f : list) {
            boolean bool = f.isFile();
            if (bool) {
                from = f.getAbsolutePath();
                from = from.replace("\\", "\\\\");
                sop(from);
                unzip(from, to);
            }
        }
    }

    public static void sop(Object obj) {
        LogUtil.info(obj);
    }

    public static void seperate(char c) {
        for (int x = 0; x < 100; x++) {
            System.out.print(c);
        }
        sop("");
    }

    public static void getFiles(HashTree tree, List<BodyFile> files) {
        for (Object key : tree.keySet()) {
            HashTree node = tree.get(key);
            if (key instanceof HTTPSamplerProxy) {
                HTTPSamplerProxy source = (HTTPSamplerProxy) key;
                if (source != null && source.getHTTPFiles().length > 0) {
                    for (HTTPFileArg arg : source.getHTTPFiles()) {
                        BodyFile file = new BodyFile();
                        file.setId(arg.getParamName());
                        file.setName(arg.getPath());
                        files.add(file);
                    }
                }
            } else if (key instanceof CSVDataSet) {
                CSVDataSet source = (CSVDataSet) key;
                if (source != null && source.getFilename() != null) {
                    BodyFile file = new BodyFile();
                    file.setId(source.getFilename());
                    file.setName(source.getFilename());
                    files.add(file);
                }
            }
            if (node != null) {
                getFiles(node, files);
            }
        }
    }

    @SuppressWarnings("finally")
    public static File downloadFile(String urlPath, String downloadDir) {
        OutputStream out = null;
        BufferedInputStream bin = null;
        HttpURLConnection httpURLConnection = null;
        try {
            URL url = new URL(urlPath);
            URLConnection urlConnection = url.openConnection();
            httpURLConnection = (HttpURLConnection) urlConnection;// http的连接类
            httpURLConnection.setConnectTimeout(1000 * 5);//设置超时
            httpURLConnection.setRequestMethod("GET");//设置请求方式，默认是GET
            httpURLConnection.setRequestProperty("Charset", "UTF-8");// 设置字符编码
            httpURLConnection.connect();// 打开连接

            bin = new BufferedInputStream(httpURLConnection.getInputStream());
            String fileName = httpURLConnection.getHeaderField("Content-Disposition");
            fileName = URLDecoder.decode(fileName.substring(fileName.indexOf("filename") + 10, fileName.length() - 1), "UTF-8");
            String path = downloadDir + File.separatorChar + fileName;// 指定存放位置
            File file = new File(path);
            // 校验文件夹目录是否存在，不存在就创建一个目录
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            out = new FileOutputStream(file);
            int size = 0;

            byte[] b = new byte[2048];
            //把输入流的文件读取到字节数据b中，然后输出到指定目录的文件
            while ((size = bin.read(b)) != -1) {
                out.write(b, 0, size);
            }
            // 关闭资源
            bin.close();
            out.close();
            LogUtil.info("文件下载成功！");
            return file;
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            LogUtil.error(e);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            LogUtil.error(e);

            LogUtil.info("文件下载失败！");
        } finally {
            try {
                if (bin != null) {
                    bin.close();
                }
                if (out != null) {
                    out.close();
                }
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            } catch (Exception e) {

            }
        }
        return null;
    }
}
