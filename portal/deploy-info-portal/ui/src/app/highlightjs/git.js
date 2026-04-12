/**
 * Language: Git Status & Log
 * Description: Highlighting for git status --long and git log --oneline output.
 * Author: Custom Definition
 * Website: https://git-scm.com/docs/git-status
 * Category: common, scripting
 */

/** @type LanguageFn */
export default function(hljs) {
  const { regex } = hljs;

  return {
    name: 'Git Status',
    aliases: ['git'],
    contains: [
      // Specific Branch/Remote Info
      {
        scope: 'symbol',
        begin: regex.either(
          /^On branch\s+.+$/,
          /^Your branch is\s+.+$/
        ),
        relevance: 10
      },
      // General status messages
      {
        scope: 'meta',
        begin: /^nothing to commit, working tree clean$/,
        relevance: 10
      },
      // Commit Hashes for 'git log'
      {
        scope: 'attr',
        begin: /^[a-f0-9]{7,40}\s/,
        relevance: 10
      },
      // Instructions in parentheses
      {
        scope: 'comment',
        begin: /\(use\s+.+\)/,
        relevance: 0
      },
      // Section Headers
      {
        scope: 'keyword',
        begin: regex.either(
          /^Changes to be committed:$/,
          /^Changes not staged for commit:$/,
          /^Untracked files:$/,
          /^Unmerged paths:$/
        ),
        relevance: 0
      },
      // Staged/Modified file entries
      {
        begin: /^\s+(modified|new file|deleted|renamed|typechange):\s+/,
        end: /$/,
        contains: [
          {
            scope: 'string',
            begin: /.+/
          }
        ]
      },
      // Untracked files/Paths
      {
        scope: 'string',
        begin: /^\s+([a-zA-Z0-9/._\s-]+\s*)$/,
        relevance: 1
      }
    ]
  };
}