A miner for comparing git merges between traditional textual merge tools and semistructured merge.

- If you want to mine a local repository, follow [local](https://github.com/spgroup/s3m/tree/master/miner/local).
- If you want to mine a web repository, follow [web](https://github.com/spgroup/s3m/tree/master/miner/web).

(further instructions on each folder)

During execution, the $HOME/.jfstmerge folder will be generated, having the following files:
- conflicts.unstructured: conflicts only reported by textual merge
- conflicts.semistructured: conflicts only reported by semistructured merge
- conflicts.equal: similar conflicts between the two tools
- jfstmerge.summary: a summary of the collected statistics
- jfstmerge.statistics: statistics collected by merged file
- jfstmerge.statistics.scenario: statistics collected by merge scenario

(open with any text editor)
