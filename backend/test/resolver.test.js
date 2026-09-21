const assert = require("assert");
const ResolverManager = require("../src/resolvers/ResolverManager");
const { ErrorCode } = require("../src/types/ResolveResult");

async function runTests() {
  console.log("🧪 开始执行 Backend Resolver 测试集...\n");

  const manager = new ResolverManager();

  // 测试 1：空 URL 处理
  console.log("▶ 测试 1: 空 URL 异常拦截");
  const emptyRes = await manager.resolve("");
  assert.strictEqual(emptyRes.ok, false);
  assert.strictEqual(emptyRes.error_code, ErrorCode.INVALID_URL);
  console.log("  ✔ 通过\n");

  // 测试 2：MockResolver 完整链路（MP4 直链与作品元数据）
  console.log("▶ 测试 2: MockResolver 正常返回视频流与元数据");
  const mockRes = await manager.resolve("https://v.douyin.com/test_sample/");
  assert.strictEqual(mockRes.ok, true);
  assert.strictEqual(mockRes.platform, "douyin");
  assert.ok(mockRes.id);
  assert.ok(mockRes.title);
  assert.ok(mockRes.cover_url);
  assert.strictEqual(mockRes.media.length, 1);
  assert.strictEqual(mockRes.media[0].type, "video");
  assert.ok(mockRes.media[0].url.startsWith("http"));
  console.log("  ✔ 通过\n");

  // 测试 3：风控人工验证模拟 (NEED_USER_INTERACTION)
  console.log("▶ 测试 3: 需要人工验证分支 (NEED_USER_INTERACTION)");
  const verifyRes = await manager.resolve("https://v.douyin.com/verify_test/");
  assert.strictEqual(verifyRes.ok, false);
  assert.strictEqual(verifyRes.error_code, ErrorCode.NEED_USER_INTERACTION);
  console.log("  ✔ 通过\n");

  // 测试 4：HTML 非数据模拟 (HTML_NOT_DATA)
  console.log("▶ 测试 4: 网页返回 HTML 但缺少数据 (HTML_NOT_DATA)");
  const htmlRes = await manager.resolve("https://v.douyin.com/html_nodata_test/");
  assert.strictEqual(htmlRes.ok, false);
  assert.strictEqual(htmlRes.error_code, ErrorCode.HTML_NOT_DATA);
  console.log("  ✔ 通过\n");

  // 测试 5：作品未找到模拟 (EMPTY_DATA)
  console.log("▶ 测试 5: 作品未找到 (EMPTY_DATA)");
  const notFoundRes = await manager.resolve("https://v.douyin.com/fail_test/");
  assert.strictEqual(notFoundRes.ok, false);
  assert.strictEqual(notFoundRes.error_code, ErrorCode.EMPTY_DATA);
  console.log("  ✔ 通过\n");

  console.log("🎉 所有 Backend Resolver 核心测试全部通过！");
}

runTests().catch(err => {
  console.error("❌ 测试失败:", err);
  process.exit(1);
});
