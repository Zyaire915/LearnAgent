package com.learnagent.websearch;

import java.util.Set;
import java.util.regex.Pattern;

/** One place to maintain the explainable ranking rules. No network or model calls. */
final class ResourceReviewPolicy {
  private ResourceReviewPolicy() { }
  static final String VERSION = "R7";
  static final Set<String> OFFICIAL = Set.of("docs.oracle.com", "dev.java", "docs.python.org",
      "developer.mozilla.org", "docs.spring.io", "spring.io", "learn.microsoft.com", "kubernetes.io",
      "postgresql.org", "vuejs.org", "react.dev", "nodejs.org",
      "go.dev", "rust-lang.org", "kotlinlang.org", "swift.org", "typescriptlang.org",
      "developer.android.com", "developer.apple.com", "docs.flutter.dev", "dart.dev",
      "docs.djangoproject.com", "flask.palletsprojects.com", "fastapi.tiangolo.com",
      "docs.docker.com", "git-scm.com", "docs.github.com", "docs.gitlab.com",
      "dev.mysql.com", "sqlite.org", "redis.io", "mongodb.com", "docs.aws.amazon.com",
      "cloud.google.com", "help.aliyun.com", "cloud.tencent.com", "support.huaweicloud.com",
      "numpy.org", "pandas.pydata.org", "scipy.org", "pytorch.org", "tensorflow.org", "scikit-learn.org",
      "docs.julialang.org", "r-project.org", "developer.huawei.com");
  static final Set<String> COMMUNITY = Set.of("youtube.com", "youtu.be", "bilibili.com", "csdn.net", "zhihu.com", "stackoverflow.com");
  // Explicit institutions, not a blanket trust bonus for every educational suffix.
  static final Set<String> ACADEMIC = Set.of(
      "mit.edu", "stanford.edu", "harvard.edu", "berkeley.edu", "cmu.edu", "yale.edu", "princeton.edu",
      "tsinghua.edu.cn", "pku.edu.cn", "zju.edu.cn", "sjtu.edu.cn", "ustc.edu.cn", "nju.edu.cn",
      "ox.ac.uk", "cam.ac.uk", "imperial.ac.uk", "ucl.ac.uk", "ed.ac.uk", "open.ac.uk",
      "ethz.ch", "epfl.ch", "tum.de", "rwth-aachen.de", "tudelft.nl", "kth.se", "helsinki.fi",
      "utoronto.ca", "ubc.ca", "uwaterloo.ca", "mcgill.ca", "anu.edu.au", "unimelb.edu.au", "unsw.edu.au",
      "u-tokyo.ac.jp", "kyoto-u.ac.jp", "nus.edu.sg", "ntu.edu.sg", "hku.hk", "cuhk.edu.hk",
      "iitm.ac.in", "iitb.ac.in", "iisc.ac.in", "kaist.ac.kr", "snu.ac.kr", "usp.br", "unam.mx", "uct.ac.za");
  static final Set<String> EDUCATION = Set.of("coursera.org", "edx.org", "icourse163.org", "xuetangx.com",
      "khanacademy.org", "freecodecamp.org", "nptel.ac.in", "swayam.gov.in", "openstax.org", "futurelearn.com", "open.edu", "mooc.fi");
  // Focused course portals improve recall without restricting the general search.
  static final java.util.List<String> COURSE_SEARCH = java.util.List.of("ocw.mit.edu", "online.stanford.edu",
      "cs50.harvard.edu", "oyc.yale.edu", "open.edu", "ocw.tudelft.nl", "mooc.fi",
      "icourse163.org", "xuetangx.com", "coursera.org", "edx.org", "nptel.ac.in");
  static final Set<String> ENGLISH_STOP_WORDS = Set.of("a", "an", "the", "and", "or", "of", "for", "to", "in", "on", "with", "is");
  static final int TRUSTED_DOMAIN = 35, COMMUNITY_DOMAIN = 12, EDUCATION_DOMAIN = 24;
  static final int TITLE_RELEVANCE = 40, SUMMARY_RELEVANCE = 10, NO_RELEVANCE = -30;
  static final int ATTRIBUTION = 8, RECENT_DATE = 5, OLDER_DATE = 2;
  static final int PROMOTIONAL = -30, CAUTION_THRESHOLD = 20;
  static final long RECENT_DAYS = 365, OLDER_DAYS = 1095;
  static final int MIN_DUPLICATE_SUMMARY_LENGTH = 40;
  // Specific disclosures/calls to action, not broad topic words such as 广告、采集、转载.
  static final Pattern PROMOTION = Pattern.compile(
      "领券购买|领取优惠券|限时抢购|扫码购买|付费推广|商业推广|广告推广|"
          + "(?:^|[\\r\\n；;【\\[])\\s*(?:广告内容|推广内容|转载自|采集自)|"
          + "\\b(?:sponsored\\s+(?:post|content|advertisement)|buy\\s+now|limited[- ]time\\s+offer)\\b",
      Pattern.CASE_INSENSITIVE);
}
