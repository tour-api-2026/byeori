import { api, unwrap, ApiEnvelope } from './client';
import { CommentTag } from './types';

export function fetchCommentTags(): Promise<CommentTag[]> {
  return unwrap<CommentTag[]>(api.get<ApiEnvelope<CommentTag[]>>('/comment-tags'));
}

export type ContentTagCount = { commentTagId: number; name: string; count: number; voted: boolean };

export function fetchContentTags(targetType: string, targetId: number): Promise<ContentTagCount[]> {
  return unwrap<ContentTagCount[]>(api.get<ApiEnvelope<ContentTagCount[]>>('/content-tags', { params: { targetType, targetId } }));
}

export function voteTag(body: { commentTagId: number; targetType: string; targetId: number }): Promise<void> {
  return unwrap<void>(api.post<ApiEnvelope<void>>('/content-tag-votes', body));
}

export function unvoteTag(body: { commentTagId: number; targetType: string; targetId: number }): Promise<void> {
  return unwrap<void>(api.delete<ApiEnvelope<void>>('/content-tag-votes', { data: body }));
}
