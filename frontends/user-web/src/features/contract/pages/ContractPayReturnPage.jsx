import { useEffect, useRef } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import StatusMessage from '../components/StatusMessage';
import StepNav from '../components/StepNav';
import { abortPayment } from '../lib/api';

// 카카오페이 cancel_url / fail_url 화면: /contract/pay?result=cancel|fail&applicationNo=..&orderId=..
// 서버에 결제 시도를 닫고, 결제 화면으로 돌려보낸다
export default function ContractPayReturnPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const applicationNo = params.get('applicationNo');
  const orderId = params.get('orderId');
  const result = params.get('result') === 'cancel' ? 'cancel' : 'fail';
  const started = useRef(false);

  useEffect(() => {
    if (started.current || !applicationNo || !orderId) return;
    started.current = true;
    // 기록에 실패해도 사용자는 다시 결제할 수 있으므로 화면은 그대로 진행한다
    abortPayment(applicationNo, { orderId, result })
      .catch(() => {})
      .finally(() => navigate(`/contract/${applicationNo}/pay?result=${result}`, { replace: true }));
  }, [applicationNo, orderId, result, navigate]);

  if (!applicationNo || !orderId) return <Navigate to="/apply/room" replace />;

  return (
    <>
      <StepNav current={3} />
      <StatusMessage title="결제 화면으로 돌아가는 중이에요" />
    </>
  );
}
