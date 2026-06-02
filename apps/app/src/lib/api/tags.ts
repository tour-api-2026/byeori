import { api, unwrap, ApiEnvelope } from './client';
import { CommentTag } from './types';

export function fetchCommentTags(): Promise<CommentTag[]> {
  return unwrap<CommentTag[]>(api.get<ApiEnvelope<CommentTag[]>>('/comment-tags'));
}
