/**
 * 统一视频解析器接口基类
 */
class VideoResolver {
  constructor(name) {
    this.name = name;
  }

  /**
   * 判断是否支持该 URL
   * @param {string} url 
   * @returns {boolean}
   */
  canResolve(url) {
    return false;
  }

  /**
   * 解析视频元数据与媒体地址
   * @param {string} url 
   * @returns {Promise<import("../types/ResolveResult").ResolveResult>}
   */
  async resolve(url) {
    throw new Error("resolve() must be implemented by subclass");
  }
}

module.exports = VideoResolver;
